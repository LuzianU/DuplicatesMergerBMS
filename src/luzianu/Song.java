package luzianu;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class Song implements Serializable {
    public transient boolean hasBeenMerged = false;
    public transient boolean deleteMe = false;
    public transient boolean hasCollisions = false;
    private static final long serialVersionUID = 42L;
    private List<Chart> charts = new ArrayList<>();
    private List<NonChart> nonCharts = new ArrayList<>();
    protected String path;

    public Song(String path) {
        this.path = path;
    }

    public boolean hasAtLeastOneChart() {
        return !charts.isEmpty();
    }

    public void addChart(Chart chart) {
        charts.add(chart);
    }

    public void addNonChart(NonChart nonChart) {
        nonCharts.add(nonChart);
    }

    public void addNonCharts(Collection<NonChart> collection) {
        nonCharts.addAll(collection);
    }

    public void addCharts(Collection<Chart> collection) {
        charts.addAll(collection);
    }

    public String getPath() {
        return path;
    }

    public Song clone() {
        Song clone = new Song(path);
        clone.charts.addAll(charts);
        clone.nonCharts.addAll(nonCharts);
        return clone;
    }

    public List<Chart> getCharts() {
        return charts;
    }

    public List<NonChart> getNonCharts() {
        return nonCharts;
    }

    public long calculateFileSize() {
        long totalSize = 0;
        for (Chart chart : charts)
            totalSize += chart.getFileSize();
        for (NonChart nonChart : nonCharts)
            totalSize += nonChart.getFileSize();

        return totalSize;
    }

    /**
     * @param song
     * @return true if this song's charts and the given one have one or more same md5
     * false if they don't, or if they have the same path
     */
    public boolean hasCommonMd5With(Song song) {
        if (path.equals(song.getPath()))
            return false;

        for (Chart a : charts) {
            for (Chart b : song.getCharts()) {
                if (a.getMd5().equals(b.getMd5())) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean hasMd5(String md5) {
        boolean contains = false;

        for (Chart chart : charts) {
            if (chart.getMd5().equals(md5)) {
                contains = true;
                break;
            }
        }

        return contains;
    }

    //@Override
    //public boolean equals(Object obj) {
    //    if (!(obj instanceof Song))
    //        return false;
//
    //    return hasCommonMd5With((Song) obj);
    //}

    public boolean mergeWouldCollideWith(Song song) {
        return false;
    }

    @Override
    public String toString() {
        return path + ", charts: " + charts.size() + ", non-charts: " + nonCharts.size();
    }

}
