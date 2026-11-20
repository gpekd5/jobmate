package com.jobmate.common.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class PiiMasker {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern EMAIL = Pattern.compile("[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", Pattern.CASE_INSENSITIVE);
    private static final Pattern PHONE = Pattern.compile("(?<!\\d)(?:\\+82[- .]?)?0?(?:1[016789]|[2-6][1-5]?)[) .-]?\\d{3,4}[- .]?\\d{4}(?!\\d)");
    private static final Pattern IDENTITY = Pattern.compile("(?<!\\d)\\d{6}[- ]?[1-8]\\d{6}(?!\\d)");
    private static final Pattern SENSITIVE_LINE = Pattern.compile("(?im)^\\s*(?:이름|성명|성함|name|주소|address|연락처|전화번호|휴대폰|생년월일|주민등록번호|계좌번호|계좌)\\s*(?:[:：=]|[ \\t]+)[^\\r\\n]*$");
    private static final Pattern LABELED_ADDRESS = Pattern.compile("(?i)(?:상세\\s*)?(?:주소|address)\\s*[:：=]\\s*[^\\r\\n\"}]*");
    private static final Pattern ADDRESS = Pattern.compile("(?:서울|부산|대구|인천|광주|대전|울산|세종|경기|강원|충북|충남|전북|전남|경북|경남|제주)[^\\n\\r\"]{0,60}(?:(?:로|길)\\s*\\d+|(?:동|읍|면|리)\\s*\\d+)(?:[- ]\\d+)?(?:\\s*\\d+(?:동|호))?");

    public String mask(String input) {
        if (input == null) return "";
        String stripped = input.stripLeading();
        if (stripped.startsWith("{") || stripped.startsWith("[")) {
            try {
                return maskJson(JSON.readTree(input)).toString();
            } catch (JsonProcessingException exception) {
                // 문서 원문이 JSON으로 시작해도 일반 텍스트 마스킹은 적용한다.
            }
        }
        return maskText(input);
    }

    private JsonNode maskJson(JsonNode value) {
        if (value.isTextual()) return TextNode.valueOf(mask(value.textValue()));
        if (value instanceof ObjectNode object) {
            object.properties().forEach(entry -> object.set(entry.getKey(), maskJson(entry.getValue())));
        } else if (value instanceof ArrayNode array) {
            for (int index = 0; index < array.size(); index++) array.set(index, maskJson(array.get(index)));
        }
        return value;
    }

    private String maskText(String input) {
        String result = SENSITIVE_LINE.matcher(input).replaceAll("[개인정보 마스킹]");
        result = LABELED_ADDRESS.matcher(result).replaceAll("[상세주소 마스킹]");
        result = EMAIL.matcher(result).replaceAll("[이메일 마스킹]");
        result = PHONE.matcher(result).replaceAll("[연락처 마스킹]");
        result = IDENTITY.matcher(result).replaceAll("[식별번호 마스킹]");
        return ADDRESS.matcher(result).replaceAll("[상세주소 마스킹]");
    }
}
