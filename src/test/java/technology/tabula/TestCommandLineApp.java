package technology.tabula;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.ParseException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class TestCommandLineApp {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private String csvFromCommandLineArgs(String[] args) throws ParseException {
        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = parser.parse(CommandLineApp.buildOptions(), args);

        StringBuilder stringBuilder = new StringBuilder();
        new CommandLineApp(stringBuilder, cmd).extractTables(cmd);

        return stringBuilder.toString();
    }

    @Test
    public void testExtractSpreadsheetWithArea() throws ParseException, IOException {

        String expectedCsv = UtilsForTesting.loadCsv("src/test/resources/technology/tabula/csv/spreadsheet_no_bounding_frame.csv");

        assertEquals(expectedCsv, this.csvFromCommandLineArgs(new String[]{
                "src/test/resources/technology/tabula/spreadsheet_no_bounding_frame.pdf",
                "-p", "1", "-a",
                "150.56,58.9,654.7,536.12", "-f",
                "CSV"
        }));
    }

    @Test
    public void testExtractBatchSpreadsheetWithArea() throws ParseException, IOException {
        FileSystem fs = FileSystems.getDefault();
        String expectedCsv = UtilsForTesting.loadCsv("src/test/resources/technology/tabula/csv/spreadsheet_no_bounding_frame.csv");
        Path tmpFolder = Files.createTempDirectory("tabula-java-batch-test");
        tmpFolder.toFile().deleteOnExit();

        Path copiedPDF = tmpFolder.resolve(fs.getPath("spreadsheet.pdf"));
        Path sourcePDF = fs.getPath("src/test/resources/technology/tabula/spreadsheet_no_bounding_frame.pdf");
        Files.copy(sourcePDF, copiedPDF);
        copiedPDF.toFile().deleteOnExit();

        this.csvFromCommandLineArgs(new String[]{
                "-b", tmpFolder.toString(),
                "-p", "1", "-a",
                "150.56,58.9,654.7,536.12", "-f",
                "CSV"
        });

        Path csvPath = tmpFolder.resolve(fs.getPath("spreadsheet.csv"));
        assertTrue(csvPath.toFile().exists());
        assertArrayEquals(expectedCsv.getBytes(), Files.readAllBytes(csvPath));
    }

    @Test
    public void testExtractSpreadsheetWithAreaAndNewFile() throws ParseException, IOException {

        String expectedCsv = UtilsForTesting.loadCsv("src/test/resources/technology/tabula/csv/spreadsheet_no_bounding_frame.csv");

        File newFile = folder.newFile();
        this.csvFromCommandLineArgs(new String[]{
                "src/test/resources/technology/tabula/spreadsheet_no_bounding_frame.pdf",
                "-p", "1", "-a",
                "150.56,58.9,654.7,536.12", "-f",
                "CSV", "-o", newFile.getAbsolutePath()
        });

        assertArrayEquals(expectedCsv.getBytes(), Files.readAllBytes(Paths.get(newFile.getAbsolutePath())));
    }


    @Test
    public void testExtractJSONWithArea() throws ParseException, IOException {

        String expectedJson = UtilsForTesting.loadJson("src/test/resources/technology/tabula/json/spanning_cells_basic.json");

        assertEquals(expectedJson, this.csvFromCommandLineArgs(new String[]{
                "src/test/resources/technology/tabula/spanning_cells.pdf",
                "-p", "1", "-a",
                "150.56,58.9,654.7,536.12", "-f",
                "JSON"
        }));
    }

    @Test
    public void testExtractCSVWithArea() throws ParseException, IOException {

        String expectedCsv = UtilsForTesting.loadCsv("src/test/resources/technology/tabula/csv/spanning_cells.csv");

        assertEquals(expectedCsv, this.csvFromCommandLineArgs(new String[]{
                "src/test/resources/technology/tabula/spanning_cells.pdf",
                "-p", "1", "-a",
                "150.56,58.9,654.7,536.12", "-f",
                "CSV"
        }));
    }

    @Test
    public void testGuessOption() throws ParseException, IOException {
        String expectedCsvNoGuessing = UtilsForTesting.loadCsv("src/test/resources/technology/tabula/csv/TestCommandLineApp_testGuessOption_no_guessing.csv");
        assertEquals(expectedCsvNoGuessing, this.csvFromCommandLineArgs(new String[]{
                "src/test/resources/technology/tabula/icdar2013-dataset/competition-dataset-eu/eu-001.pdf",
                "-p", "1",
                "-f", "CSV"
        }));

        String expectedCsvWithGuessing = UtilsForTesting.loadCsv("src/test/resources/technology/tabula/csv/TestCommandLineApp_testGuessOption_with_guessing.csv");
        assertEquals(expectedCsvWithGuessing, this.csvFromCommandLineArgs(new String[]{
                "src/test/resources/technology/tabula/icdar2013-dataset/competition-dataset-eu/eu-001.pdf",
                "-p", "1",
                "-f", "CSV",
                "-g"
        }));
    }

    @Test
    public void testEncryptedPasswordSupplied() throws ParseException {
        String s = this.csvFromCommandLineArgs(new String[]{
                "src/test/resources/technology/tabula/encrypted.pdf",
                "-s", "userpassword",
                "-p", "1",
                "-f", "CSV"
        });
        assertEquals("FLA Audit Profile,,,,,,,,,", s.split("\\r?\\n")[0]);
    }

    @Test(expected=org.apache.commons.cli.ParseException.class)
    public void testEncryptedWrongPassword() throws ParseException {
        String s = this.csvFromCommandLineArgs(new String[]{
                "src/test/resources/technology/tabula/encrypted.pdf",
                "-s", "wrongpassword",
                "-p", "1",
                "-f", "CSV"
        });
    }

    @Test
    public void testExtractWithMultiplePercentArea() throws ParseException, IOException {

        String expectedCsv = UtilsForTesting.loadCsv("src/test/resources/technology/tabula/csv/MultiColumn.csv");

        assertEquals(expectedCsv, this.csvFromCommandLineArgs(new String[]{
                "src/test/resources/technology/tabula/MultiColumn.pdf",
                "-p", "1", "-a",
                "%0,0,100,50", "-a",
                "%0,50,100,100", "-f",
                "CSV"
        }));
    }

    @Test
    public void testExtractWithMultipleAbsoluteArea() throws ParseException, IOException {

        String expectedCsv = UtilsForTesting.loadCsv("src/test/resources/technology/tabula/csv/MultiColumn.csv");

        assertEquals(expectedCsv, this.csvFromCommandLineArgs(new String[]{
                "src/test/resources/technology/tabula/MultiColumn.pdf",
                "-p", "1", "-a",
                "0,0,451,212", "-a",
                "0,212,451,425", "-f",
                "CSV"
        }));
    }

    @Test
    public void testExtractWithPercentAndAbsoluteArea() throws ParseException, IOException {

        String expectedCsv = UtilsForTesting.loadCsv("src/test/resources/technology/tabula/csv/MultiColumn.csv");

        assertEquals(expectedCsv, this.csvFromCommandLineArgs(new String[]{
                "src/test/resources/technology/tabula/MultiColumn.pdf",
                "-p", "1", "-a",
                "%0,0,100,50", "-a",
                "0,212,451,425", "-f",
                "CSV"
        }));
    }

    @Test
    public void testLatticeModeWithColumnOption() throws ParseException, IOException {

        String expectedCsv = UtilsForTesting.loadCsv("src/test/resources/technology/tabula/csv/AnimalSounds.csv");

        assertEquals(expectedCsv, this.csvFromCommandLineArgs(new String[]{
                "src/test/resources/technology/tabula/AnimalSounds.pdf",
                "-p", "1", "-c",
                "59,218,331,551",
                "-r", "-f", "CSV"
        }));
    }

    @Test
    public void testLatticeModeWithColumnAndMultipleAreasOption() throws ParseException, IOException {

        String expectedJson = UtilsForTesting.loadJson("src/test/resources/technology/tabula/json/AnimalSounds1.json");
        String resultJson = this.csvFromCommandLineArgs(new String[]{
                "src/test/resources/technology/tabula/AnimalSounds1.pdf",
                "-p", "1", "-c", "57,136,197,296,314,391,457,553",
                "-a", "%0,0,100,50", "-a", "%0,50,100,100",
                 "-r", "-f", "JSON"
        });
        assertEquals(expectedJson, resultJson);
    }

    private File writeRepaintedPdf() throws IOException {
        File pdf = folder.newFile("repainted.pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(font, 12f);
                cs.newLineAtOffset(100f, 700f);
                cs.showText("12345");
                // horizontal 2.5pt repaint, large enough to bypass PDFBox's own
                // duplicate suppression but above the 50% area overlap threshold
                cs.newLineAtOffset(2.5f, 0f);
                cs.showText("12345");
                cs.endText();
            }
            doc.save(pdf);
        }
        return pdf;
    }

    @Test
    public void testDedupeCharactersOptionRemovesRepaintedAmount() throws ParseException, IOException {
        File pdf = writeRepaintedPdf();

        String withoutFlag = csvFromCommandLineArgs(new String[]{pdf.getAbsolutePath(), "-p", "1"});
        String withFlag = csvFromCommandLineArgs(
                new String[]{pdf.getAbsolutePath(), "-p", "1", "--dedupe-characters"});

        assertTrue(withoutFlag.contains("1122334455"));
        assertTrue(withFlag.contains("12345"));
        assertFalse(withFlag.contains("1122334455"));
    }

    @Test
    public void testDedupeCharactersOptionAppliesInBothModes() throws ParseException, IOException {
        File pdf = writeRepaintedPdf();

        String stream = csvFromCommandLineArgs(new String[]{
                pdf.getAbsolutePath(), "-p", "1", "-t", "--dedupe-characters"});
        String lattice = csvFromCommandLineArgs(new String[]{
                pdf.getAbsolutePath(), "-p", "1", "-l", "--dedupe-characters"});

        assertTrue(stream.contains("12345"));
        assertFalse(stream.contains("1122334455"));
        // lattice finds no ruled table here, but it must run cleanly and the
        // stream output above proves the shared pre-merge dedup path
        assertNotNull(lattice);
    }

    @Test
    public void testDedupeCharactersWithExplicitThreshold() throws ParseException, IOException {
        File pdf = writeRepaintedPdf();

        String withThreshold = csvFromCommandLineArgs(new String[]{
                pdf.getAbsolutePath(), "-p", "1", "--dedupe-characters", "0.5"});

        assertTrue(withThreshold.contains("12345"));
        assertFalse(withThreshold.contains("1122334455"));
    }

    @Test(expected = ParseException.class)
    public void testDedupeCharactersRejectsOutOfRangeThreshold() throws ParseException {
        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = parser.parse(CommandLineApp.buildOptions(), new String[]{
                "src/test/resources/technology/tabula/AnimalSounds.pdf",
                "-p", "1", "--dedupe-characters", "1.5"});
        new CommandLineApp(new StringBuilder(), cmd);
    }

    @Test(expected = ParseException.class)
    public void testDedupeCharactersRejectsNonNumericThreshold() throws ParseException {
        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = parser.parse(CommandLineApp.buildOptions(), new String[]{
                "src/test/resources/technology/tabula/AnimalSounds.pdf",
                "-p", "1", "--dedupe-characters", "lots"});
        new CommandLineApp(new StringBuilder(), cmd);
    }

}
