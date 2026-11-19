package com.jobmate.profile.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobmate.common.ai.AiGateway;
import com.jobmate.common.ai.AiPurpose;
import com.jobmate.common.exception.CustomException;
import com.jobmate.profile.application.command.AnalyzeProfileCommand;
import com.jobmate.profile.application.event.ProfileDataDeletedEvent;
import com.jobmate.profile.application.model.ProfileContent;
import com.jobmate.profile.application.result.AnalysisResult;
import com.jobmate.profile.application.result.ConsentResult;
import com.jobmate.profile.application.result.DocumentResult;
import com.jobmate.profile.application.result.PortfolioLinkResult;
import com.jobmate.profile.domain.entity.AnalysisStatus;
import com.jobmate.profile.domain.entity.DocumentStatus;
import com.jobmate.profile.domain.entity.DocumentType;
import com.jobmate.profile.domain.entity.PortfolioLink;
import com.jobmate.profile.domain.entity.ProfileAnalysis;
import com.jobmate.profile.domain.entity.ProfileDocument;
import com.jobmate.profile.domain.exception.ProfileErrorCode;
import com.jobmate.profile.infrastructure.GithubReadmeClient;
import com.jobmate.profile.infrastructure.PrivateDocumentStorage;
import com.jobmate.profile.infrastructure.SafeDocumentExtractor;
import com.jobmate.profile.infrastructure.repository.PortfolioLinkRepository;
import com.jobmate.profile.infrastructure.repository.ProfileAnalysisRepository;
import com.jobmate.profile.infrastructure.repository.ProfileDocumentRepository;
import com.jobmate.profile.infrastructure.repository.ProfileStateRepository;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Executor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

// 단일 로컬 소유자의 문서 수명, 검토 프로필, 동의와 명시 분석 요청을 관리한다.
@Service
@Transactional
public class ProfileService {

    private final ProfileStateRepository states;
    private final ProfileDocumentRepository documents;
    private final PortfolioLinkRepository links;
    private final ProfileAnalysisRepository analyses;
    private final PrivateDocumentStorage storage;
    private final SafeDocumentExtractor extractor;
    private final GithubReadmeClient github;
    private final ObjectMapper mapper;
    private final Validator validator;
    private final AiGateway gateway;
    private final ProfileAnalysisWorker worker;
    private final Executor executor;
    private final ApplicationEventPublisher events;
    private final ProfileFileCleanup cleanup;

    public ProfileService(ProfileStateRepository states, ProfileDocumentRepository documents,
            PortfolioLinkRepository links, ProfileAnalysisRepository analyses, PrivateDocumentStorage storage,
            SafeDocumentExtractor extractor, GithubReadmeClient github, ObjectMapper mapper, Validator validator,
            AiGateway gateway, ProfileAnalysisWorker worker, @Qualifier("aiTaskExecutor") Executor executor,
            ApplicationEventPublisher events, ProfileFileCleanup cleanup) {
        this.states = states;
        this.documents = documents;
        this.links = links;
        this.analyses = analyses;
        this.storage = storage;
        this.extractor = extractor;
        this.github = github;
        this.mapper = mapper;
        this.validator = validator;
        this.gateway = gateway;
        this.worker = worker;
        this.executor = executor;
        this.events = events;
        this.cleanup = cleanup;
    }

    public DocumentResult upload(DocumentType type, MultipartFile file) {
        if (file.getSize() > SafeDocumentExtractor.MAX_BYTES) {
            throw new CustomException(ProfileErrorCode.DOCUMENT_TOO_LARGE);
        }
        String name = safeFileName(file.getOriginalFilename());
        String extension = extractor.extension(name, file.getContentType());
        byte[] bytes;
        try (var input = file.getInputStream()) {
            bytes = input.readNBytes(SafeDocumentExtractor.MAX_BYTES + 1);
        } catch (IOException exception) {
            throw new CustomException(ProfileErrorCode.INVALID_DOCUMENT);
        }
        extractor.extract(bytes, extension);
        cleanup.drain();
        var state = states.lockLocal();
        String key = storage.save(bytes, extension);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    storage.delete(key);
                }
            }
        });
        if (type == DocumentType.RESUME) {
            for (var old : documents.findAllByOwnerIdOrderByIdAsc(1)) {
                if (old.getType() == DocumentType.RESUME) {
                    cleanup.schedule(old.getStorageKey());
                    documents.delete(old);
                    state.removePersonalData(false);
                    clearAnalyses();
                }
            }
        }
        return DocumentResult.from(documents.save(ProfileDocument.create(type, name, key)));
    }

    @Transactional(readOnly = true)
    public List<DocumentResult> documents() {
        return documents.findAllByOwnerIdOrderByIdAsc(1).stream().map(DocumentResult::from).toList();
    }

    public void deleteDocument(Long id) {
        var state = states.lockLocal();
        var document = documents.findByIdAndOwnerId(id, 1)
                .orElseThrow(() -> new CustomException(ProfileErrorCode.DOCUMENT_NOT_FOUND));
        cleanup.schedule(document.getStorageKey());
        documents.delete(document);
        state.removePersonalData(true);
        clearAnalyses();
        events.publishEvent(new ProfileDataDeletedEvent(1));
    }

    public PortfolioLinkResult addLink(String url, String description) {
        states.lockLocal();
        return PortfolioLinkResult.from(links.save(PortfolioLink.create(github.canonicalUrl(url), description)));
    }

    @Transactional(readOnly = true)
    public List<PortfolioLinkResult> links() {
        return links.findAllByOwnerIdOrderByIdAsc(1).stream().map(PortfolioLinkResult::from).toList();
    }

    public void deleteLink(Long id) {
        var state = states.lockLocal();
        var link = links.findByIdAndOwnerId(id, 1)
                .orElseThrow(() -> new CustomException(ProfileErrorCode.PORTFOLIO_LINK_NOT_FOUND));
        links.delete(link);
        state.removePersonalData(true);
        clearAnalyses();
        events.publishEvent(new ProfileDataDeletedEvent(1));
    }

    public AnalysisResult analyze(AnalyzeProfileCommand command) {
        var state = states.lockLocal();
        if (!"EXTERNAL_LLM".equals(command.analysisMode())) {
            throw new CustomException(ProfileErrorCode.UNSUPPORTED_ANALYSIS_MODE);
        }
        if (!state.isConsented() || !state.getPurposes().contains(AiPurpose.PROFILE_EXTRACTION)) {
            throw new CustomException(ProfileErrorCode.AI_CONSENT_REQUIRED);
        }
        if (command.documentIds().isEmpty() || command.documentIds().stream().distinct().count() != command.documentIds().size()
                || command.portfolioLinkIds().stream().distinct().count() != command.portfolioLinkIds().size()) {
            throw new CustomException(ProfileErrorCode.INVALID_PROFILE_INPUT);
        }
        var selected = command.documentIds().stream().map(id -> documents.findByIdAndOwnerId(id, 1)
                .orElseThrow(() -> new CustomException(ProfileErrorCode.DOCUMENT_NOT_FOUND))).toList();
        if (selected.stream().noneMatch(document -> document.getType() == DocumentType.RESUME)) {
            throw new CustomException(ProfileErrorCode.RESUME_REQUIRED);
        }
        for (Long id : command.portfolioLinkIds()) {
            links.findByIdAndOwnerId(id, 1)
                    .orElseThrow(() -> new CustomException(ProfileErrorCode.PORTFOLIO_LINK_NOT_FOUND));
        }
        if (selected.stream().anyMatch(document -> !storage.exists(document.getStorageKey()))) {
            throw new CustomException(ProfileErrorCode.DOCUMENT_STORAGE_ERROR);
        }
        String modelVersion = gateway.modelVersion();
        if (command.portfolioLinkIds().isEmpty()) {
            // 원격 README는 내용 버전을 보장할 수 없으므로 문서만 선택한 성공 결과를 재사용한다.
            var cached = analyses.findAllByOwnerIdOrderByIdAsc(1).stream()
                    .filter(analysis -> analysis.reusableFor(command.documentIds(), state,
                            modelVersion, ProfileAnalysisWorker.PROMPT_VERSION))
                    .reduce((older, newer) -> newer);
            if (cached.isPresent()) {
                return AnalysisResult.from(cached.get());
            }
        }
        if (analyses.existsByOwnerIdAndStatusIn(1, List.of(AnalysisStatus.PENDING, AnalysisStatus.RUNNING))) {
            throw new CustomException(ProfileErrorCode.ANALYSIS_ALREADY_RUNNING);
        }
        gateway.assertAvailable(AiPurpose.PROFILE_EXTRACTION);
        selected.forEach(document -> document.mark(DocumentStatus.ANALYZING));
        var analysis = analyses.saveAndFlush(ProfileAnalysis.create(command.documentIds(), command.portfolioLinkIds(), state,
                modelVersion, ProfileAnalysisWorker.PROMPT_VERSION));
        Long id = analysis.getId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    executor.execute(() -> worker.run(id));
                } catch (RuntimeException exception) {
                    worker.fail(id, "AI_BUSY");
                }
            }
        });
        return AnalysisResult.from(analysis);
    }

    @Transactional(readOnly = true)
    public AnalysisResult analysis(Long id) {
        return AnalysisResult.from(analyses.findByIdAndOwnerId(id, 1)
                .orElseThrow(() -> new CustomException(ProfileErrorCode.ANALYSIS_NOT_FOUND)));
    }

    public void confirm(Long expectedVersion, boolean confirmed, ProfileContent content) {
        var state = states.lockLocal();
        if (!confirmed || !documents.existsByOwnerIdAndType(1, DocumentType.RESUME)) {
            throw new CustomException(ProfileErrorCode.RESUME_REQUIRED);
        }
        if (expectedVersion == null || expectedVersion != state.getProfileVersion()) {
            throw new CustomException(ProfileErrorCode.PROFILE_CONFLICT);
        }
        var violations = validator.validate(content);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
        state.confirm(mapper.valueToTree(content));
    }

    public JsonNode preferences(JsonNode value) {
        var state = states.lockLocal();
        state.changePreferences(value);
        return state.getPreferences().deepCopy();
    }

    @Transactional(readOnly = true)
    public ConsentResult consent() {
        var state = states.findById(1L).orElseThrow();
        return new ConsentResult("OPENAI", state.getPurposes(), state.isConsented(), state.getConsentVersion());
    }

    public ConsentResult consent(String provider, List<AiPurpose> purposes, boolean allowed) {
        if (!"OPENAI".equals(provider) || (allowed && purposes.isEmpty())
                || purposes.stream().distinct().count() != purposes.size()) {
            throw new CustomException(ProfileErrorCode.INVALID_PROFILE_INPUT);
        }
        var state = states.lockLocal();
        state.changeConsent(allowed, allowed ? purposes : List.of());
        for (var analysis : analyses.findAllByOwnerIdOrderByIdAsc(1)) {
            if (analysis.getStatus() == AnalysisStatus.PENDING || analysis.getStatus() == AnalysisStatus.RUNNING) {
                analysis.fail("AI_CONSENT_CHANGED");
            }
        }
        for (var document : documents.findAllByOwnerIdOrderByIdAsc(1)) {
            if (document.getAnalysisStatus() == DocumentStatus.ANALYZING) {
                document.mark(DocumentStatus.FAILED);
            }
        }
        return new ConsentResult("OPENAI", state.getPurposes(), state.isConsented(), state.getConsentVersion());
    }

    private void clearAnalyses() {
        analyses.deleteAllByOwnerId(1);
        for (var document : documents.findAllByOwnerIdOrderByIdAsc(1)) {
            if (document.getAnalysisStatus() == DocumentStatus.ANALYZING) {
                document.mark(DocumentStatus.NOT_ANALYZED);
            }
        }
    }

    private String safeFileName(String original) {
        if (original == null || original.length() > 200 || original.chars().anyMatch(Character::isISOControl)
                || original.contains("\\") || !Path.of(original).getFileName().toString().equals(original)) {
            throw new CustomException(ProfileErrorCode.INVALID_DOCUMENT);
        }
        return original;
    }
}
