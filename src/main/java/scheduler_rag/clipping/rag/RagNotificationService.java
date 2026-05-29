package scheduler_rag.clipping.rag;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.reader.pdf.ParagraphPdfDocumentReader;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class RagNotificationService {

    private final VectorStore vectorStore;

    @Value("classpath:/docs/boletim_diario.pdf")
    private Resource newsPDF;

    public void ingestPDF() {
        try {
            var pdfReader = new ParagraphPdfDocumentReader(newsPDF).get();
            vectorStore.add(pdfReader);
        } catch (RuntimeException ex) {
            log.error("Error ingesting PDF document: {}", ex.getMessage(), ex);
            throw ex;
        }
    }

}
