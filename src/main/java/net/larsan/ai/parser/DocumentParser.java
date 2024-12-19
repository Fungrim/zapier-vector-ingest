package net.larsan.ai.parser;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MimeType;
import org.apache.tika.mime.MimeTypeException;
import org.apache.tika.mime.MimeTypes;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.jboss.logging.Logger;

import com.google.common.base.Strings;

import dev.langchain4j.data.document.Document;
import io.quarkus.tika.TikaParser;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import net.larsan.ai.api.Encoding;
import net.larsan.ai.api.UpsertRequest;
import net.larsan.ai.conf.MetadataConfig;

@Singleton
public class DocumentParser {

    private static final MediaType MARKDOWN = MediaType.parse("text/markdown");

    @Inject
    MetadataConfig metadataConfig;

    @Inject
    Logger log;

    @Inject
    MarkdownStripper markdownStripper;

    @Inject
    HtmlStripper htmlStripper;

    @Inject
    TikaParser parser;

    public Document parse(UpsertRequest req, Metadata parseMetadata) {
        Document d = createDocument(req, parseMetadata);
        MimeType ct = tryGetMimeType(parseMetadata);
        if (isHtml(ct)) {
            d = htmlStripper.strip(d, parseMetadata);
        } else if (isMarkdown(ct)) {
            d = markdownStripper.strip(d, parseMetadata);
        } else {
            d = Document.document(d.text().trim());
        }
        return d;
    }

    private MimeType tryGetMimeType(Metadata parseMetadata) {
        String ct = parseMetadata.get(Metadata.CONTENT_TYPE);
        if (Strings.isNullOrEmpty(ct)) {
            return null;
        } else {
            try {
                return MimeTypes.getDefaultMimeTypes().forName(ct);
            } catch (MimeTypeException e) {
                log.warn("Failed to parse media type: " + ct, e);
                return null;
            }
        }
    }

    private boolean isHtml(MimeType t) {
        return t == null ? false : t.getType().getBaseType().equals(MediaType.TEXT_HTML);
    }

    private boolean isMarkdown(MimeType t) {
        return t == null ? false : t.getType().getBaseType().equals(MARKDOWN);
    }

    private Document createDocument(UpsertRequest req, Metadata parseMetadata) {
        if (metadataConfig.contentType().enabled() && req.data().contentType().isPresent()) {
            parseMetadata.set(Metadata.CONTENT_TYPE, req.data().contentType().orElse("application/octet-stream"));
        }
        if (metadataConfig.fileName().enabled() && req.data().fileName().isPresent()) {
            parseMetadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, req.data().fileName().orElse("unknown"));
        }
        byte[] content = req.data().encoding().orElse(Encoding.UTF8).toCleartext(req.data().content());
        try (InputStream tikaStream = TikaInputStream.get(new ByteArrayInputStream(content))) {
            Parser p = new AutoDetectParser();
            BodyContentHandler handler = new BodyContentHandler(-1);
            p.parse(tikaStream, handler, parseMetadata, new ParseContext());
            return Document.document(handler.toString());
        } catch (Exception e) {
            log.error("Failed to parse document", e);
            throw new IllegalStateException(e);
        }

        /*
         * TikaContent cont = null;
         * if (Strings.isNullOrEmpty(parseMetadata.get(Metadata.CONTENT_TYPE))) {
         * cont = parser.parse(new ByteArrayInputStream(content));
         * } else {
         * cont = parser.parse(new ByteArrayInputStream(content), parseMetadata.get(Metadata.CONTENT_TYPE));
         * }
         * if (cont.getMetadata() != null) {
         * final TikaMetadata m = cont.getMetadata();
         * m.getNames().forEach(n -> {
         * parseMetadata.set(n, m.getSingleValue(n));
         * });
         * }
         * return Document.document(cont.getText());
         */
    }
}