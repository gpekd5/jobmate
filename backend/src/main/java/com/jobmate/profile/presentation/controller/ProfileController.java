package com.jobmate.profile.presentation.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jobmate.common.presentation.ApiResponse;
import com.jobmate.profile.application.model.ProfileSnapshot;
import com.jobmate.profile.application.result.AnalysisResult;
import com.jobmate.profile.application.result.ConsentResult;
import com.jobmate.profile.application.result.DocumentResult;
import com.jobmate.profile.application.result.PortfolioLinkResult;
import com.jobmate.profile.application.service.ProfileQueryService;
import com.jobmate.profile.application.service.ProfileService;
import com.jobmate.profile.domain.entity.DocumentType;
import com.jobmate.profile.presentation.request.AnalysisRequest;
import com.jobmate.profile.presentation.request.ConsentRequest;
import com.jobmate.profile.presentation.request.PortfolioLinkRequest;
import com.jobmate.profile.presentation.request.PreferencesRequest;
import com.jobmate.profile.presentation.request.SaveProfileRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/me")
@Profile("local")
@Validated
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;
    private final ProfileQueryService profileQueryService;
    private final ObjectMapper mapper;

    @PostMapping(value = "/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<DocumentResult>> upload(@RequestParam DocumentType type,
            @RequestParam MultipartFile file) {
        DocumentResult result = profileService.upload(type, file);
        return ResponseEntity.created(URI.create("/api/v1/me/documents/" + result.documentId()))
                .body(ApiResponse.success(result));
    }

    @GetMapping("/documents")
    public ApiResponse<List<DocumentResult>> documents() {
        return ApiResponse.success(profileService.documents());
    }

    @DeleteMapping("/documents/{id}")
    public ResponseEntity<Void> deleteDocument(@PathVariable @Positive Long id) {
        profileService.deleteDocument(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/portfolio-links")
    public ResponseEntity<ApiResponse<PortfolioLinkResult>> addLink(@Valid @RequestBody PortfolioLinkRequest request) {
        var result = profileService.addLink(request.url(), request.description());
        return ResponseEntity.created(URI.create("/api/v1/me/portfolio-links/" + result.linkId()))
                .body(ApiResponse.success(result));
    }

    @GetMapping("/portfolio-links")
    public ApiResponse<List<PortfolioLinkResult>> links() {
        return ApiResponse.success(profileService.links());
    }

    @DeleteMapping("/portfolio-links/{id}")
    public ResponseEntity<Void> deleteLink(@PathVariable @Positive Long id) {
        profileService.deleteLink(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/profile/analyses")
    public ResponseEntity<ApiResponse<AnalysisResult>> analyze(@Valid @RequestBody AnalysisRequest request) {
        return ResponseEntity.accepted().body(ApiResponse.success(profileService.analyze(request.toCommand())));
    }

    @GetMapping("/profile/analyses/{id}")
    public ApiResponse<AnalysisResult> analysis(@PathVariable @Positive Long id) {
        return ApiResponse.success(profileService.analysis(id));
    }

    @GetMapping("/profile")
    public ApiResponse<JsonNode> profile() {
        return ApiResponse.success(profileJson(profileQueryService.current()));
    }

    @PutMapping("/profile")
    public ApiResponse<JsonNode> confirm(@Valid @RequestBody SaveProfileRequest request) {
        profileService.confirm(request.profileVersion(), request.confirmed(), request.content());
        return profile();
    }

    @GetMapping("/preferences")
    public ApiResponse<JsonNode> preferences() {
        return ApiResponse.success(profileQueryService.current().preferences());
    }

    @PutMapping("/preferences")
    public ApiResponse<JsonNode> preferences(@Valid @RequestBody PreferencesRequest request) {
        return ApiResponse.success(profileService.preferences(mapper.valueToTree(request)));
    }

    @GetMapping("/ai-consent")
    public ApiResponse<ConsentResult> consent() {
        return ApiResponse.success(profileService.consent());
    }

    @PutMapping("/ai-consent")
    public ApiResponse<ConsentResult> consent(@Valid @RequestBody ConsentRequest request) {
        return ApiResponse.success(profileService.consent(request.provider(), request.purposes(), request.consented()));
    }

    private JsonNode profileJson(ProfileSnapshot snapshot) {
        ObjectNode content = (ObjectNode) snapshot.profile().deepCopy();
        content.put("profileVersion", snapshot.profileVersion());
        content.put("confirmed", snapshot.confirmed());
        return content;
    }
}
