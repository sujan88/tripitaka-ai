package com.sujan.tripitaka.ingest;

import com.sujan.tripitaka.model.SourceText;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Run once with the ingest profile, e.g.
 * <pre>
 * ./mvnw spring-boot:run -Dspring-boot.run.profiles=ingest \
 *   -Dspring-boot.run.arguments="--format=suttacentral --path=/path/to/bilara-data/translation/en/sujato/sutta/mn"
 * </pre>
 * format: suttacentral (SuttaCentral bilara-data JSON; "bilara" also works), pdf, or tipitakalk (Sinhala, github.com/pathnirvana/tipitaka.lk
 * public/static/text). Optional --nikaya for PDFs; optional --include=dn-,mn- (file prefixes) for tipitakalk.
 * Ingest Sinhala into its own collection: TRIPITAKA_COLLECTION=sutta-si.
 */
@Component
@Profile("ingest")
public class IngestionRunner implements ApplicationRunner {

    private final IngestionService ingestionService;
    private final String collection;

    public IngestionRunner(IngestionService ingestionService, @Value("${rag.collection}") String collection) {
        this.ingestionService = ingestionService;
        this.collection = collection;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        String format = single(args, "format", "bilara");
        if ("prune".equals(format)) {
            // Clean-up only: drop retired releases of TRIPITAKA_COLLECTION without importing anything.
            ingestionService.pruneRetired(collection);
            return;
        }
        Path path = Path.of(single(args, "path", null));
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("--path does not exist: " + path);
        }

        List<SourceText> texts = switch (format) {
            // "bilara" is the old name (SuttaCentral's bilara-data format), kept so existing commands still work.
            case "suttacentral", "bilara" -> new SuttaCentralReader().read(path);
            case "pdf" -> new PdfReader(single(args, "nikaya", null)).read(path);
            case "tipitakalk" -> new TipitakaLkReader(List.of(single(args, "include", "dn-,mn-,sn-,an-,kn-").split(",")))
                    .read(path);
            default -> throw new IllegalArgumentException("--format must be suttacentral, pdf, tipitakalk or prune");
        };
        ingestionService.ingest(collection, texts);
    }

    private static String single(ApplicationArguments args, String name, String fallback) {
        List<String> values = args.getOptionValues(name);
        if (values == null || values.isEmpty()) {
            if (fallback == null) {
                throw new IllegalArgumentException("Missing --" + name);
            }
            return fallback;
        }
        return values.get(0);
    }
}
