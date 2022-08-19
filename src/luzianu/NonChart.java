package luzianu;

import java.io.Serializable;

public class NonChart implements Serializable {
    private static final long serialVersionUID = 42L;
    private String path;
    private long fileSize;
    private String originalPath;

    public NonChart(String path, long fileSize) {
        this.path = path;
        this.fileSize = fileSize;
    }

    public long getFileSize() {
        return fileSize;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public void setOriginalPath(String originalPath) {
        this.originalPath = originalPath;
    }

    public String getOriginalPath() {
        return originalPath;
    }
}
