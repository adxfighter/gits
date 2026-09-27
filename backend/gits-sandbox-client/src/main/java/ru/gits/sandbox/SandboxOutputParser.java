package ru.gits.sandbox;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Parses the entrypoint output protocol (see sandbox/java/README.md). The JUnit report is base64-encoded.
 * Candidate code controls the test console output and may print fake markers there. The entrypoint
 * always prints its own markers after the test JVM has exited, so the last occurrence of each marker
 * is the authentic one.
 */
public final class SandboxOutputParser {

    static final String COMPILE_ERROR = "===GITS-COMPILE-ERROR===";
    static final String OUTPUT_BEGIN = "===GITS-OUTPUT-BEGIN===";
    static final String OUTPUT_END = "===GITS-OUTPUT-END===";
    static final String REPORT_BEGIN = "===GITS-REPORT-BEGIN===";
    static final String REPORT_END = "===GITS-REPORT-END===";

    private SandboxOutputParser() {
    }

    public static ParsedRun parse(SandboxRun run) {
        String out = run.output();
        int reportEnd = out.lastIndexOf(REPORT_END);
        int reportBegin = reportEnd < 0 ? -1 : out.lastIndexOf(REPORT_BEGIN, reportEnd);
        if (reportBegin < 0) {
            // No authentic report block: compilation error, killed or failed run
            int compile = out.indexOf(COMPILE_ERROR);
            if (run.exitCode() == 2 && compile >= 0) {
                return new ParsedRun(false, out.substring(compile + COMPILE_ERROR.length()).strip(), "", List.of(), false);
            }
            return new ParsedRun(true, null, tail(out), List.of(), false);
        }
        String encoded = out.substring(reportBegin + REPORT_BEGIN.length(), reportEnd).strip();
        int outputBegin = out.indexOf(OUTPUT_BEGIN);
        int outputEnd = out.lastIndexOf(OUTPUT_END, reportBegin);
        String console = outputBegin >= 0 && outputEnd > outputBegin
                ? out.substring(outputBegin + OUTPUT_BEGIN.length(), outputEnd).strip()
                : "";
        if (encoded.isEmpty()) {
            return new ParsedRun(true, null, console, List.of(), false);
        }
        String report = new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
        return new ParsedRun(true, null, console, parseReport(report), true);
    }

    static List<TestCaseResult> parseReport(String xml) {
        try {
            DocumentBuilder builder = secureFactory().newDocumentBuilder();
            builder.setErrorHandler(new DefaultHandler());  // fatal errors throw; nothing is printed to stderr
            var document = builder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
            NodeList nodes = document.getElementsByTagName("testcase");
            List<TestCaseResult> cases = new ArrayList<>(nodes.getLength());
            for (int i = 0; i < nodes.getLength(); i++) {
                Element testCase = (Element) nodes.item(i);
                cases.add(new TestCaseResult(testCase.getAttribute("classname"), testCase.getAttribute("name"),
                        status(testCase), message(testCase)));
            }
            return cases;
        } catch (Exception e) {
            throw new IllegalArgumentException("Malformed JUnit report", e);
        }
    }

    /** XML parser with DTDs and external entities disabled (the report passes through candidate-controlled output). */
    private static DocumentBuilderFactory secureFactory() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        return factory;
    }

    private static TestCaseResult.Status status(Element testCase) {
        if (testCase.getElementsByTagName("failure").getLength() > 0) {
            return TestCaseResult.Status.FAILED;
        }
        if (testCase.getElementsByTagName("error").getLength() > 0) {
            return TestCaseResult.Status.ERROR;
        }
        if (testCase.getElementsByTagName("skipped").getLength() > 0) {
            return TestCaseResult.Status.SKIPPED;
        }
        return TestCaseResult.Status.PASSED;
    }

    private static String message(Element testCase) {
        for (String tag : List.of("failure", "error")) {
            NodeList nodes = testCase.getElementsByTagName(tag);
            if (nodes.getLength() > 0) {
                Element element = (Element) nodes.item(0);
                String message = element.getAttribute("message");
                if (message.isEmpty()) {
                    message = element.getAttribute("type");
                }
                return message.length() > TestCaseResult.MAX_MESSAGE
                        ? message.substring(0, TestCaseResult.MAX_MESSAGE)
                        : message;
            }
        }
        return null;
    }

    private static String tail(String output) {
        return output.length() <= 4096 ? output : output.substring(output.length() - 4096);
    }
}
