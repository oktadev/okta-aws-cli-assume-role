package com.okta.tools.io;

import org.jsoup.Jsoup;
import org.jsoup.nodes.DataNode;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.select.Elements;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

/**
 * <p>
 * Inspired by a find/replace workaround:
 * https://stackoverflow.com/questions/52572853/failed-integrity-metadata-check-in-javafx-webview-ignores-systemprop
 * </p>
 * <p>
 *     {@literal @bogeylnj} built the original version of this fix and had this comment:
 *     <blockquote>
 *         "javaFX.WebEngine with >1.8.0._162 cannot handle "integrity=" (attribute &lt;link&gt; or &lt;script&gt;) checks on files retrievals properly.
 *         This custom stream handler will disable the integrity checks by replacing "integrity=" and "integrity =" with a "integrity.disabled" counterpart
 *         This is very susceptible to breaking if Okta changes the response body again as we are making changes based on the format of the characters in their response"
 *     </blockquote>
 * </p>
 * <p>
 * The current fix expands on the find/replace solution by using JSoup to do a robust HTML5 parse to find and disable
 * the integrity assertions within the DOM and JavaScript content. If I was feeling particularly bold, I'd parse the
 * JavaScript with a JavaScript parser, but I like sleep and people using broken software like timely fixes.
 * </p>
 */
final class SubresourceIntegrityStrippingURLConnection extends URLConnection {
    private static final Logger LOGGER = Logger.getLogger(SubresourceIntegrityStrippingURLConnection.class.getName());
    private final URLConnection httpsURLConnection;

    SubresourceIntegrityStrippingURLConnection(URL url, URLConnection httpsURLConnection) {
        super(url);
        this.httpsURLConnection = httpsURLConnection;
    }

    @Override
    public void connect() throws IOException {
        httpsURLConnection.connect();
    }

    @Override
    public InputStream getInputStream() throws IOException {
        try {
            Document document = Jsoup.parse(
                    httpsURLConnection.getInputStream(),
                    StandardCharsets.UTF_8.name(),
                    httpsURLConnection.getURL().toURI().toASCIIString()
            );
            LOGGER.finest(document::toString);
            Elements scriptsAssertingIntegrity = document.select("script:containsData(integrity)");
            for (Element scriptAssertingIntegrity : scriptsAssertingIntegrity) {
                String scriptWithSuppressedIntegrity = scriptAssertingIntegrity.data()
                        .replace("integrity", "integrityDisabled");
                for (Node dataNode : scriptAssertingIntegrity.dataNodes()) {
                    dataNode.remove();
                }
                scriptAssertingIntegrity.appendChild(new DataNode(scriptWithSuppressedIntegrity));
            }
            document.select("script[integrity^=sha]").removeAttr("integrity");
            LOGGER.finest(document::toString);
            return new ByteArrayInputStream(document.toString().getBytes(StandardCharsets.UTF_8));
        } catch (URISyntaxException e) {
            throw new IOException(e);
        }
    }

    @Override
    public OutputStream getOutputStream() throws IOException {
        return httpsURLConnection.getOutputStream();
    }
}
