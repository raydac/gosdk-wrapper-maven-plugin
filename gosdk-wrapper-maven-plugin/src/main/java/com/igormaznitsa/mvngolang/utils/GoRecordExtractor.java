package com.igormaznitsa.mvngolang.utils;

import static com.igormaznitsa.mvngolang.GoRecordChecksum.MD5;
import static com.igormaznitsa.mvngolang.GoRecordChecksum.SHA256;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Stream.concat;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.igormaznitsa.mvngolang.GoRecord;
import com.igormaznitsa.mvngolang.GoRecordChecksum;
import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Attribute;
import org.jsoup.parser.Parser;
import org.jsoup.select.Elements;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

public class GoRecordExtractor {

  private static final String JSON_LINK = "link";
  private static final String JSON_CHECKSUM = "checksum";

  private static final Pattern SDK_NAME_PATTERN = Pattern.compile("([a-zA-Z\\d]+(?:\\.\\d+)+).*");
  private static final Pattern SHA256PATTERN = Pattern.compile("\\b[A-Fa-f0-9]{64}\\b");

  private static final GoRecordExtractor INSTANCE = new GoRecordExtractor();

  private GoRecordExtractor() {

  }

  public static GoRecordExtractor getInstance() {
    return INSTANCE;
  }

  public static String concatUrl(final String baseUri, final String rest) {
    String trimmedRest = rest.trim();
    if (baseUri == null
        || baseUri.isBlank()
        || trimmedRest.toLowerCase(Locale.ROOT).startsWith("file:")
        || trimmedRest.toLowerCase(Locale.ROOT).startsWith("http:")
        || trimmedRest.toLowerCase(Locale.ROOT).startsWith("https:")
    ) {
      return trimmedRest;
    } else {
      final URI uri = URI.create(baseUri);
      return uri.resolve(rest).toASCIIString();
    }
  }

  public static Optional<String> extractSdkVersion(final String name) {
    final Matcher matcher = SDK_NAME_PATTERN.matcher(name.trim());
    if (matcher.find()) {
      return Optional.of(matcher.group(1));
    }
    return Optional.empty();
  }

  protected static Optional<List<GoRecord>> tryAsPlainText(final String baseUrl,
                                                           final String text) {
    try {
      final Map<String, List<GoRecord.GoFile>> records = new HashMap<>();
      text.lines().map(String::trim).filter(x -> !x.isBlank() && !x.startsWith("#"))
          .forEach(x -> {
            final String[] split = x.split(",");
            final String fileName;
            final String fileLink;
            final String sha256;
            if (split.length == 3) {
              fileName = split[0].trim();
              fileLink = split[1].trim();
              sha256 = split[2].trim();
            } else if (split.length == 2) {
              fileName = split[0].trim();
              fileLink = split[1].trim();
              sha256 = null;
            } else {
              throw new IllegalArgumentException("Wrong line: " + x);
            }

            final Matcher nameMatcher = SDK_NAME_PATTERN.matcher(fileName);
            final String goSdkVersion;
            if (nameMatcher.find()) {
              goSdkVersion = nameMatcher.group(1);
            } else {
              throw new IllegalArgumentException("Non-golang sdk file name: " + fileName);
            }

            if (sha256 != null && !SHA256PATTERN.matcher(sha256).matches()) {
              throw new IllegalArgumentException("SNA256 is wrong " + sha256);
            }

            final GoRecord.GoFile fileRecord =
                new GoRecord.GoFile(fileName, concatUrl(baseUrl, fileLink),
                    sha256 == null ? Map.of() : Map.of(
                        SHA256, sha256));

            records.merge(goSdkVersion, List.of(fileRecord),
                (a, b) -> concat(a.stream(), b.stream()).collect(toList()));
          });

      return Optional.of(records.entrySet().stream()
          .map(e -> new GoRecord(e.getKey(), e.getValue()))
          .sorted(Comparator.comparing(GoRecord::getName))
          .collect(toList()));
    } catch (Exception c) {
      return Optional.empty();
    }
  }

  protected static Optional<List<GoRecord>> tryAsSdkSiteHtml(final String baseUrl,
                                                             final String text) {
    try {
      final org.jsoup.nodes.Document document = Jsoup.parse(text, baseUrl, Parser.htmlParser());
      final Elements trTags = document.getElementsByTag("tr");
      final Map<String, List<GoRecord.GoFile>> records = new HashMap<>();
      for (final org.jsoup.nodes.Element tr : trTags) {
        String name = null;
        String fileName = null;
        String fileLink = null;
        String sha256 = null;
        final Elements tdTags = tr.getElementsByTag("td");
        for (final org.jsoup.nodes.Element td : tdTags) {
          if (tr.equals(td.parent())) {
            final String fullText = td.text();
            final Matcher sha256Matcher = SHA256PATTERN.matcher(fullText);
            if (sha256Matcher.matches()) {
              sha256 = fullText.trim();
            }
            final Elements anchors = td.getElementsByTag("a");
            for (final org.jsoup.nodes.Element anchor : anchors) {
              final String anchorText = anchor.ownText().trim();
              final Matcher matcher = SDK_NAME_PATTERN.matcher(anchorText);
              if (matcher.find()) {
                name = matcher.group(1);
                fileName = anchorText;
                final Attribute href = anchor.attribute("href");
                if (href != null) {
                  fileLink = concatUrl(baseUrl, href.getValue());
                }
              }
            }
          }
        }

        if (name != null && fileLink != null) {
          final GoRecord.GoFile nextFile = new GoRecord.GoFile(fileName, fileLink,
              sha256 == null ? Map.of() : Map.of(SHA256, sha256));
          records.merge(name, List.of(nextFile),
              (a, b) -> concat(a.stream(), b.stream()).collect(toList()));
        }
      }

      return Optional.of(records.entrySet().stream()
          .map(e -> new GoRecord(e.getKey(), e.getValue()))
          .sorted(Comparator.comparing(GoRecord::getName))
          .collect(toList()));

    } catch (Exception ex) {
      return Optional.empty();
    }
  }

  protected static Optional<List<GoRecord>> tryAsGoogleApisXml(final String baseUri,
                                                               final String text) {
    try {
      final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      final DocumentBuilder builder = factory.newDocumentBuilder();
      builder.setErrorHandler(new ErrorHandler() {
        @Override
        public void warning(SAXParseException exception) throws SAXException {
          // do nothing
        }

        @Override
        public void error(SAXParseException exception) throws SAXException {
          // do nothing
        }

        @Override
        public void fatalError(SAXParseException exception) throws SAXException {
          // do nothing
        }
      });
      final Document document = builder.parse(new InputSource(new StringReader(text)));

      final Map<String, List<GoRecord.GoFile>> recordMap = new HashMap<>();

      final Element root = document.getDocumentElement();
      if ("ListBucketResult".equals(root.getTagName())) {
        final NodeList list = root.getElementsByTagName("Contents");
        for (int i = 0; i < list.getLength(); i++) {
          final Element element = (Element) list.item(i);
          final NodeList keys = element.getElementsByTagName("Key");
          final NodeList eTags = element.getElementsByTagName("ETag");

          if (keys.getLength() != 0) {
            final String keyString = keys.item(0).getTextContent();
            final Matcher matcher = SDK_NAME_PATTERN.matcher(keyString);
            if (matcher.find()) {
              final String version = matcher.group(1);
              String md5 = null;
              if (eTags.getLength() > 0) {
                md5 = eTags.item(0).getTextContent();
                if (md5.startsWith("\"")) {
                  md5 = md5.substring(1);
                }
                if (md5.endsWith("\"")) {
                  md5 = md5.substring(0, md5.length() - 1);
                }
              }
              recordMap.merge(version,
                  List.of(new GoRecord.GoFile(keyString, concatUrl(baseUri, keyString),
                      md5 == null ? Map.of() : Map.of(MD5, md5))),
                  (a, b) -> concat(a.stream(), b.stream()).collect(toList()));
            }
          }
        }

        return Optional.of(recordMap.entrySet().stream()
            .map(e -> new GoRecord(e.getKey(), e.getValue()))
            .sorted(Comparator.comparing(GoRecord::getName))
            .collect(toList()));
      } else {
        return Optional.empty();
      }
    } catch (ParserConfigurationException | SAXException | IOException ex) {
      return Optional.empty();
    }

  }

  protected static Optional<List<GoRecord>> tryAsJsonObject_v1(final String baseUri,
                                                               final String text) {
    try {
      final List<GoRecord> records = new ArrayList<>();
      final JsonObject list =
          JsonParser.parseReader(new JsonReader(new StringReader(text))).getAsJsonObject();

      var sdkMap = list.asMap();
      for (var w : sdkMap.entrySet()) {
        final String sdkName = w.getKey();
        final JsonElement sdkRecord = w.getValue();
        final List<GoRecord.GoFile> goFiles = new ArrayList<>();

        var recordMap = sdkRecord.getAsJsonObject().asMap();
        for (var rr : recordMap.entrySet()) {
          final String fileName = rr.getKey();
          final JsonElement fileData = rr.getValue();
          final String fileLink;
          final Map<GoRecordChecksum, String> checksums;
          if (fileData.isJsonPrimitive()) {
            fileLink = fileData.getAsString();
            checksums = Map.of();
          } else if (fileData.isJsonObject()) {
            final JsonObject fileDataObject = fileData.getAsJsonObject();
            fileLink = concatUrl(baseUri, fileDataObject.get(JSON_LINK).getAsString());
            if (fileDataObject.has(JSON_CHECKSUM)) {
              checksums = fileDataObject.getAsJsonObject(JSON_CHECKSUM).asMap()
                  .entrySet()
                  .stream()
                  .flatMap(entry -> GoRecordChecksum.find(entry.getKey()).stream()
                      .map(cs -> Map.entry(cs, entry.getValue().getAsString())))
                  .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            } else {
              checksums = Map.of();
            }
          } else {
            throw new IllegalArgumentException("Unsupported object in JSON");
          }
          goFiles.add(new GoRecord.GoFile(fileName, fileLink, checksums));
        }

        records.add(new GoRecord(sdkName, goFiles));
      }
      records.sort(Comparator.comparing(GoRecord::getName));
      return Optional.of(records);
    } catch (Exception ex) {
      return Optional.empty();
    }
  }

  public Optional<List<GoRecord>> findRecords(final String baseUri, final String text) {
    List<GoRecord> result = tryAsPlainText(baseUri, text).orElse(null);

    if (result == null) {
      result = tryAsGoogleApisXml(baseUri, text).orElse(null);
    }

    if (result == null) {
      result = tryAsJsonObject_v1(baseUri, text).orElse(null);
    }

    if (result == null) {
      result = tryAsSdkSiteHtml(baseUri, text).orElse(null);
    }

    if (result == null) {
      result = tryAsPlainText(baseUri, text).orElse(null);
    }

    return Optional.ofNullable(result);
  }
}
