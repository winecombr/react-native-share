package cl.json;

import android.net.Uri;
import android.os.Environment;
import android.util.Base64;
import android.webkit.MimeTypeMap;

import com.facebook.react.bridge.ReactApplicationContext;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Created by disenodosbbcl on 22-07-16.
 */
public class ShareFile {

    public static final int BASE_64_DATA_LENGTH = 5; // `data:`
    public static final int BASE_64_DATA_OFFSET = 8; // `;base64,`
    private final ReactApplicationContext reactContext;
    private String url;
    private Uri uri;
    private String type;
    private String filename;

    public ShareFile(String url, String type, String filename, ReactApplicationContext reactContext) {
        this(url, filename, reactContext);
        this.type = type;
        this.filename = filename;
    }

    public ShareFile(String url, String filename, ReactApplicationContext reactContext) {
        this.url = url;
        this.uri = Uri.parse(this.url);
        this.reactContext = reactContext;
        this.filename = filename;
    }

    /**
     * Obtain mime type from URL
     *
     * @param url {@link String}
     * @return {@link String} mime type
     */
    private String getMimeType(String url) {
        String type = null;
        String extension = MimeTypeMap.getFileExtensionFromUrl(url);
        if (extension != null) {
            type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
        }
        return type;
    }

    /**
     * Return an if the url is a file (local or base64)l
     *
     * @return {@link boolean}
     */
    public boolean isFile() {
        return this.isBase64File() || this.isLocalFile();
    }

    private boolean isBase64File() {
        String scheme = uri.getScheme();

        return scheme != null && scheme.equals("data");
    }

    /**
     * Extract mime-type from a base64 resource
     *
     * @param uri {@link Uri}
     * @return {@link String}
     */
    private String getMimeTypeFromBase64(Uri uri) {
        StringBuilder type = new StringBuilder();
        char[] parts = uri.toString().substring(BASE_64_DATA_LENGTH).toCharArray();
        for (char part : parts) {
            if (part == ';') {
                break;
            }
            type.append(part);
        }

        return type.toString();
    }

    /**
     * Extract mime-type from a file resource
     *
     * @param uri {@link Uri}
     * @return {@link String}
     */
    private String getMimeTypeFromFile(Uri uri) {

        String typeFromURI = this.getMimeType(uri.toString());

        if (typeFromURI != null) {
            return typeFromURI;
        }

        // try resolving the file and get the mimetype
        String realPath = this.getRealPathFromURI(uri);

        if (realPath == null) {
            return null;
        }

        return this.getMimeType(realPath);
    }

    private boolean isLocalFile() {
        String scheme = uri.getScheme();
        return scheme != null && (scheme.equals("content") || scheme.equals("file"));
    }

    public String getType() {
        if (this.type != null) {
            return this.type;
        }

        if (isBase64File()) {
            this.type = this.getMimeTypeFromBase64(this.uri);

            return this.type;
        }

        if(isLocalFile()) {
            this.type = this.getMimeTypeFromFile(this.uri);
        }

        return "*/*";
    }

    private String getRealPathFromURI(Uri contentUri) {
        String result = RNSharePathUtil.getRealPathFromURI(this.reactContext, contentUri);
        return result;
    }

    public Uri getURI() {

        final MimeTypeMap mime = MimeTypeMap.getSingleton();
        String extension = mime.getExtensionFromMimeType(getType());

        if (this.isBase64File()) {
            String encodedImg = this.uri.toString().substring(BASE_64_DATA_LENGTH + this.type.length() + BASE_64_DATA_OFFSET);
            String filename = this.filename != null ? this.filename : System.nanoTime() + "";
            try {
                File dir = new File(this.reactContext.getExternalCacheDir(), Environment.DIRECTORY_DOWNLOADS);
                if (!dir.exists() && !dir.mkdirs()) {
                    throw new IOException("mkdirs failed on " + dir.getAbsolutePath());
                }
                File file = new File(dir, filename + "." + extension);
                final FileOutputStream fos = new FileOutputStream(file);
                fos.write(Base64.decode(encodedImg, Base64.DEFAULT));
                fos.flush();
                fos.close();
                return RNSharePathUtil.compatUriFromFile(reactContext, file);

            } catch (IOException e) {
                e.printStackTrace();
            }
        } else if (this.isLocalFile()) {
            Uri uri = Uri.parse(this.url);
            if (uri.getPath() == null) {
                return null;
            }
            return RNSharePathUtil.compatUriFromFile(reactContext, new File(uri.getPath()));
        }

        return null;
    }
}