package scheduler_rag.clipping.rag;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.ParagraphPdfDocumentReader;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class RagNotificationService {

    private final VectorStore vectorStore;

    @Value("classpath:/docs/boletim_diario.pdf")
    private Resource newsPDF;

    public List<String> ingestPDF() {
        try {
            var pdfReader = new ParagraphPdfDocumentReader(newsPDF).get();
            vectorStore.add(pdfReader);
            return pdfReader.stream().map(Document::getId).toList();
        } catch (RuntimeException ex) {
            log.error("Error ingesting PDF document: {}", ex.getMessage(), ex);
            throw ex;
        }
    }

    public String search(String query) {
        return vectorStore.similaritySearch(
                SearchRequest
                        .builder()
                        .query(query)
                        .topK(5)
                        .build())
                .stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n---\n"));
    }

    public void clearDocuments(List<String> ids) {
        vectorStore.delete(ids);
    }

}
