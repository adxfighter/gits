package ru.gits.taskbank.check;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;

/** Validates template.yaml and task.yaml against tasks/schema/*.schema.json. */
public final class SchemaCheck {

    private static final ObjectMapper YAML = new YAMLMapper();
    private static final ObjectMapper JSON = new ObjectMapper();

    private final JsonSchema templateSchema;
    private final JsonSchema taskSchema;

    public SchemaCheck(Path schemaDirectory) {
        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
        this.templateSchema = factory.getSchema(readJson(schemaDirectory.resolve("template.schema.json")));
        this.taskSchema = factory.getSchema(readJson(schemaDirectory.resolve("task.schema.json")));
    }

    public static JsonNode readYaml(Path file) {
        try {
            return YAML.readTree(file.toFile());
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + file, e);
        }
    }

    /** Empty string when valid, otherwise a list of problems. */
    public String validateTemplate(JsonNode template) {
        return describe(templateSchema.validate(template));
    }

    public String validateTask(JsonNode task) {
        return describe(taskSchema.validate(task));
    }

    private static String describe(Set<ValidationMessage> messages) {
        return messages.stream().map(ValidationMessage::getMessage).sorted().collect(Collectors.joining("; "));
    }

    private static JsonNode readJson(Path file) {
        try {
            return JSON.readTree(Files.readString(file));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read schema " + file, e);
        }
    }
}
