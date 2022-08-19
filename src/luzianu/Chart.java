package luzianu;

import java.io.Serializable;

public class Chart implements Serializable {
    private static final long serialVersionUID = 42L;
    private String md5;
    private String path;

    private String originalPath;
    private String title;
    private String artist;
    private long fileSize;

    public Chart(String md5, String path, String title, String artist, long fileSize) {
        this.md5 = md5;
        this.path = path;
        this.title = title;
        this.artist = artist;
        this.fileSize = fileSize;
    }

    public String getMd5() {
        return md5;
    }

    public String getPath() {
        return path;
    }

    public long getFileSize() {
        return fileSize;
    }

    public String getTitle() {
        return title;
    }

    public String getArtist() {
        return artist;
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
