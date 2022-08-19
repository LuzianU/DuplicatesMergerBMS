package luzianu;

import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class MergedSong extends Song {
    public transient boolean hadErrorMoving = false;

    private List<Song> consumedSongs = new ArrayList<>();
    private boolean hasConflict = false;

    public MergedSong(String path) {
        super(path);
    }

    public MergedSong(Song song, String outputDir) {
        super("");
        consumedSongs.add(song);
        for (Chart chartToAdd : song.getCharts()) {
            String relativePathChart = chartToAdd.getPath().replace(song.getPath(), "");
            String newPath = this.getPath() + relativePathChart;

            Chart chart = new Chart(chartToAdd.getMd5(),
                                    newPath,
                                    chartToAdd.getTitle(),
                                    chartToAdd.getArtist(),
                                    chartToAdd.getFileSize());
            chart.setOriginalPath(chartToAdd.getPath());

            this.addChart(chart);
        }

        for (NonChart nonChartsToAdd : song.getNonCharts()) {
            String relativePathToAdd = nonChartsToAdd.getPath().replace(song.getPath(), "");
            String newPath = this.getPath() + relativePathToAdd;

            NonChart nonChart = new NonChart(newPath, nonChartsToAdd.getFileSize());
            nonChart.setOriginalPath(nonChartsToAdd.getPath());

            this.addNonChart(nonChart);
        }

        try {
            this.setPath(Paths.get(outputDir).resolve(this.calculateFolderName()).toAbsolutePath().toString());
        } catch (Exception e) {
            e.printStackTrace();
            this.setPath(System.getProperty("java.io.tmpdir"));
        }

        updatePaths();
    }

    public void updatePaths() {
        for (Chart chart : getCharts()) {
            String newPath = this.getPath() + chart.getPath();
            chart.setPath(newPath);
        }
        for (NonChart nonChart : getNonCharts()) {
            String newPath = this.getPath() + nonChart.getPath();
            nonChart.setPath(newPath);
        }
    }

    public void setPath(String path) {
        this.path = path;
    }

    public void consume(Song song) {
        if (song.hasBeenMerged)
            assert false : "song has already been merged";

        consumedSongs.add(song);

        for (Chart chartToAdd : song.getCharts()) {
            boolean add = true;
            for (Chart chart : this.getCharts()) {
                if (chartToAdd.getMd5().equals(chart.getMd5())) {
                    add = false;
                    break;
                }
            }

            // only add new charts if the md5 is missing
            if (add) {
                String relativePathChart = chartToAdd.getPath().replace(song.getPath(), "");
                String newPath = this.getPath() + relativePathChart;

                Chart chart = new Chart(chartToAdd.getMd5(),
                                        newPath,
                                        chartToAdd.getTitle(),
                                        chartToAdd.getArtist(),
                                        chartToAdd.getFileSize());
                chart.setOriginalPath(chartToAdd.getPath());

                this.addChart(chart);
            }
        }

        if (hasConflict) {
            return;
        }

        for (NonChart nonChartsToAdd : song.getNonCharts()) {
            String relativePathToAdd = nonChartsToAdd.getPath().replace(song.getPath(), "");
            String newPath = this.getPath() + relativePathToAdd;

            NonChart nonChart = new NonChart(newPath, nonChartsToAdd.getFileSize());
            nonChart.setOriginalPath(nonChartsToAdd.getPath());

            boolean added = false;
            for (NonChart nonChartsStored : this.getNonCharts()) {
                String relativePathStored = nonChartsStored.getPath().replace(this.getPath(), "");

                if (relativePathStored.equals(relativePathToAdd)) {
                    if ((relativePathStored.endsWith(".wav") || relativePathStored.endsWith(".ogg")) &&
                        nonChartsStored.getFileSize() != nonChartsToAdd.getFileSize() &&
                        !relativePathStored.substring(relativePathStored.lastIndexOf(File.separator) + 1).startsWith("preview_")) {
                        System.err.println("conflict " + nonChartsToAdd.getPath() + " - " + nonChartsStored.getFileSize() + ":" + nonChartsToAdd.getFileSize());
                        hasConflict = true;

                        getNonCharts().clear();
                        return;
                    }

                    // set added to true but dont add to non charts since its already in there
                    added = true;
                    break;
                }
            }
            if (!added)
                this.addNonChart(nonChart);
        }
    }

    public List<Song> getConsumedSongs() {
        return consumedSongs;
    }

    public boolean isHasConflict() {
        return hasConflict;
    }

    public String calculateFolderName() {
        String title = "";
        String artist = "";

        {
            HashMap<String, Integer> titleMap = new HashMap<>();
            for (Chart c1 : getCharts()) {
                String t1 = c1.getTitle();
                for (Chart c2 : getCharts()) {
                    if (c1 == c2)
                        continue;

                    String t2 = c2.getTitle();

                    String prefix = StringUtils.getCommonPrefix(t1, t2);
                    int count = titleMap.getOrDefault(prefix, 0);
                    titleMap.put(prefix, count + 1);
                }
            }
            int highest = Integer.MIN_VALUE;
            for (String str : titleMap.keySet()) {
                if (titleMap.get(str) > highest) {
                    title = str;
                    highest = titleMap.get(str);
                }
            }
        }
        {
            HashMap<String, Integer> artistMap = new HashMap<>();

            for (Chart c1 : getCharts()) {
                String a1 = c1.getArtist();
                for (Chart c2 : getCharts()) {
                    if (c1 == c2)
                        continue;

                    String a2 = c2.getArtist();

                    String prefix = StringUtils.getCommonPrefix(a1, a2);
                    int count = artistMap.getOrDefault(prefix, 0);
                    artistMap.put(prefix, count + 1);
                }
            }
            int highest = Integer.MIN_VALUE;
            for (String str : artistMap.keySet()) {
                if (artistMap.get(str) > highest) {
                    artist = str;
                    highest = artistMap.get(str);
                }
            }
        }

        title = title.replaceAll("[./:\"*?<>|]", "").trim();
        artist = artist.replaceAll("[./:\"*?<>|]", "").trim();

        if (title.endsWith("(") || title.endsWith("["))
            title = title.substring(0, title.length() - 1).trim();
        if (artist.endsWith("(") || artist.endsWith("["))
            artist = artist.substring(0, artist.length() - 1).trim();

        if (title.isEmpty()) {
            title = getCharts().get(0).getTitle();
            title = title.replaceAll("[./:\"*?<>|]", "").trim();
            //System.err.println("fixing title to " + title);
        }
        if (artist.isEmpty()) {
            artist = getCharts().get(0).getArtist();
            artist = artist.replaceAll("[./:\"*?<>|]", "").trim();
            //System.err.println("fixing artist to " + artist);
        }

        String folderName = "[" + artist + "] " + title;

        return folderName.trim();
    }

}
