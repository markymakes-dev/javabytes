package com.javabytes.mcp.tools.springboot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.javabytes.mcp.tools.Tool;
import com.javabytes.mcp.utils.ErrorHelper;
import com.javabytes.mcp.utils.JsonUtils;

public class GenerateSpringBootSecurityTool implements Tool {

    @Override
    public String getName() { return "springboot_generate_security"; }

    @Override
    public JsonNode execute(JsonNode params) {
        String authType = params.has("authType") ? params.get("authType").asText() : "jwt";
        String packageName = params.has("packageName") ? params.get("packageName").asText() : "com.example.demo.security";

        try {
            String code = generateSecurityCode(authType, packageName);

            ObjectNode result = JsonUtils.MAPPER.createObjectNode();
            result.put("success", true);
            result.put("authType", authType);
            result.put("packageName", packageName);
            result.put("code", code);
            result.put("message", "Generated Spring Security configuration");
            return result;

        } catch (Exception e) {
            return ErrorHelper.error("GENERATE_SECURITY_ERROR", "Error generating security config: " + e.getMessage());
        }
    }

    private String generateSecurityCode(String authType, String packageName) {
        StringBuilder code = new StringBuilder();
        code.append("package ").append(packageName).append(";\n\n");

        if ("jwt".equalsIgnoreCase(authType)) {
            code.append("import org.springframework.context.annotation.Bean;\n");
            code.append("import org.springframework.context.annotation.Configuration;\n");
            code.append("import org.springframework.security.config.annotation.web.builders.HttpSecurity;\n");
            code.append("import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;\n");
            code.append("import org.springframework.security.config.http.SessionCreationPolicy;\n");
            code.append("import org.springframework.security.web.SecurityFilterChain;\n");
            code.append("import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;\n");
            code.append("\n");
            code.append("@Configuration\n");
            code.append("@EnableWebSecurity\n");
            code.append("public class SecurityConfig {\n\n");
            code.append("    @Bean\n");
            code.append("    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {\n");
            code.append("        http.csrf(csrf -> csrf.disable()\n");
            code.append("            .authorizeHttpRequests(auth -> auth\n");
            code.append("                .requestMatchers(\"/api/auth/**\").permitAll()\n");
            code.append("                .anyRequest().authenticated()\n");
            code.append("            )\n");
            code.append("            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));\n");
            code.append("        return http.build();\n");
            code.append("    }\n");
            code.append("}\n");
        } else {
            code.append("// Basic Spring Security Configuration\n");
            code.append("import org.springframework.context.annotation.Bean;\n");
            code.append("import org.springframework.context.annotation.Configuration;\n");
            code.append("import org.springframework.security.config.annotation.web.builders.HttpSecurity;\n");
            code.append("import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;\n");
            code.append("import org.springframework.security.web.SecurityFilterChain;\n");
            code.append("\n");
            code.append("@Configuration\n");
            code.append("@EnableWebSecurity\n");
            code.append("public class SecurityConfig {\n\n");
            code.append("    @Bean\n");
            code.append("    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {\n");
            code.append("        http.authorizeHttpRequests(auth -> auth.anyRequest().authenticated()\n");
            code.append("            .and().formLogin().and().httpBasic();\n");
            code.append("        return http.build();\n");
            code.append("    }\n");
            code.append("}\n");
        }
        return code.toString();
    }

    @Override
    public Tool.JsonSchema getInputSchema() {
        Tool.JsonSchema schema = new Tool.JsonSchema();
        schema.property("authType", new Tool.StringSchema().description("Auth type: jwt or basic"));
        schema.property("packageName", new Tool.StringSchema().description("Security package name (default: com.example.demo.security)"));
        return schema;
    }
}




