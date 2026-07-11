package dev.qacommons.template.model;

import java.util.List;

public record ErrorResponse(String code, String message, List<String> details) {
}
