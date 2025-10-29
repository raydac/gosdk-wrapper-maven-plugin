package com.igormaznitsa.mvngolang.utils;

import static com.igormaznitsa.mvngolang.GoRecordChecksum.MD5;
import static com.igormaznitsa.mvngolang.GoRecordChecksum.SHA256;
import static com.igormaznitsa.mvngolang.utils.GoRecordExtractor.concatUrl;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.igormaznitsa.mvngolang.GoRecord;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;

class GoRecordExtractorTest {

  private String loadResource(final String resource) throws IOException {
    return IOUtils.resourceToString("/sites/" + resource, StandardCharsets.UTF_8);
  }

  @Test
  void testConcatUri() {
    assertEquals("/dl/go1.25.1.src.tar.gz", concatUrl("", "/dl/go1.25.1.src.tar.gz"));
    assertEquals("https://go.dev/dl/go1.25.1.src.tar.gz",
        concatUrl("https://go.dev/dl/", "/dl/go1.25.1.src.tar.gz"));
    assertEquals("file:/home/hello.txt", concatUrl("https://go.dev/dl/", "file:/home/hello.txt"));
  }

  @Test
  void testParseText() throws Exception {
    final String text = this.loadResource("plain_text.txt");
    final List<GoRecord> recordList =
        GoRecordExtractor.getInstance().findRecords("", text).orElseThrow();
    assertEquals(3, recordList.size());
    final GoRecord record1 = recordList.get(0);
    final GoRecord record2 = recordList.get(1);
    final GoRecord record3 = recordList.get(2);

    assertEquals("go1.10.1", record1.getName());
    assertEquals(1, record1.getFiles().size());
    assertTrue(record1.getFiles().get(0).getChecksum().isEmpty());

    assertEquals("go1.13.4", record2.getName());
    assertEquals(1, record2.getFiles().size());
    assertNotNull(record2.getFiles().get(0).getChecksum().get(MD5));


    assertEquals("go1.25.3", record3.getName());
    assertEquals(1, record3.getFiles().size());
    assertNotNull(record3.getFiles().get(0).getChecksum().get(SHA256));
  }

  @Test
  void testParseHtml() throws Exception {
    final String text = this.loadResource("go.dev_dl_.html");
    final List<GoRecord> recordList =
        GoRecordExtractor.getInstance().findRecords("", text).orElseThrow();
    assertEquals(259, recordList.size());
    final GoRecord record = recordList.get(0);
    assertEquals("go1.10", record.getName());
    assertEquals(75, record.getFiles().size());
    assertEquals(1, record.getFiles().get(0).getChecksum().size());
    assertEquals(SHA256, record.getFiles().get(0).getChecksum().keySet().iterator().next());
  }

  @Test
  void testParseXml() throws Exception {
    final String text = this.loadResource("storage.googleapis.com.xml");
    final List<GoRecord> recordList =
        GoRecordExtractor.getInstance().findRecords("", text).orElseThrow();
    assertEquals(21, recordList.size());
  }

  @Test
  void testParseJson() throws Exception {
    final String text = this.loadResource("go_sdks.json");
    final List<GoRecord> recordList =
        GoRecordExtractor.getInstance().findRecords("", text).orElseThrow();
    assertEquals(2, recordList.size());

    final GoRecord record1 = recordList.get(0);
    assertEquals("go1.10.1", record1.getName());
    assertEquals(1, record1.getFiles().size());
    final GoRecord.GoFile file1 = record1.getFiles().get(0);
    assertEquals("go1.10.1.zip", file1.getFileName());
    assertEquals("https://go.dev/dl/go1.10.1.zip", file1.getLink());
    assertEquals(1, file1.getChecksum().size());
    assertEquals("a81a4ba593d0015e10c51e267de3ff07c7ac914dfca037d9517d029517097795",
        file1.getChecksum().get(SHA256));

    final GoRecord record2 = recordList.get(1);
    assertEquals("go1.25.3", record2.getName());
    assertEquals(1, record2.getFiles().size());
    final GoRecord.GoFile file2 = record2.getFiles().get(0);
    assertEquals("go1.25.3.tar.gz", file2.getFileName());
    assertEquals("https://go.dev/dl/go1.25.3.tar.gz", file2.getLink());
    assertTrue(file2.getChecksum().isEmpty());
  }

}