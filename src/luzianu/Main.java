package luzianu;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.CharacterIterator;
import java.text.NumberFormat;
import java.text.StringCharacterIterator;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {

    public static void main(String[] args) throws IOException {
        if (args.length == 0 || (!args[0].equals("--calc") && !args[0].equals("--merge"))) {
            System.out.println("invalid argument: Specify either \"--calc\" or \"--merge\"");
            return;
        }

        if (args[0].equals("--calc")) {
            if (args.length != 3) {
                System.out.println("Expected 3 arguments:" + System.lineSeparator() +
                                   "--calc \"folderToMergeTo\" \"bmsRootDir1;bmsRootDir2;bmsRootDir3;...\"");
                return;
            }

            String outputDir = args[1];
            System.out.println(outputDir);
            String[] bmsRootDirs = args[2].split(";");
            System.out.println(Arrays.toString(bmsRootDirs));

            File outputFile;
            try {
                outputFile = Paths.get(outputDir).toFile();
            } catch (Exception e) {
                System.err.println("output folder does not exist");
                return;
            }

            outputDir = outputFile.getAbsolutePath();

            if (!outputFile.exists() || !outputFile.isDirectory()) {
                System.err.println("output folder does not exist");
                return;
            }
            if (outputFile.list().length != 0) {
                System.err.println("output folder is not empty");
                return;
            }
            for (String dir : bmsRootDirs) {
                try {
                    if (!new File(dir).exists() || new File(dir).isFile())
                        throw new Exception();
                } catch (Exception e) {
                    System.err.println(dir + ": bms root dir does not exist");
                    return;
                }
            }

            calculateMerges(bmsRootDirs, outputDir);
        } else {
            if (!new File("merges.data").exists()) {
                System.err.println("merges.data does not exist. You have to run \"--calc\" first");
            }
            List<MergedSong> mergedSongs = readMergesFromData("merges.data");

            // Exception hell
            for (MergedSong mergedSong : mergedSongs) {
                try {
                    new File(mergedSong.getPath()).mkdirs();
                    for (Chart chart : mergedSong.getCharts()) {
                        try {
                            if (!new File(chart.getOriginalPath()).exists())
                                continue;

                            if (!Paths.get(chart.getPath()).getParent().toFile().exists())
                                Paths.get(chart.getPath()).getParent().toFile().mkdirs();

                            System.out.println(chart.getOriginalPath() + " --> " + chart.getPath());
                            Files.move(Paths.get(chart.getOriginalPath()), Paths.get(chart.getPath()), StandardCopyOption.REPLACE_EXISTING);
                        } catch (Exception e) {
                            mergedSong.hadErrorMoving = true;
                            e.printStackTrace();
                        }
                    }
                    for (NonChart nonChart : mergedSong.getNonCharts()) {
                        try {
                            if (!new File(nonChart.getOriginalPath()).exists())
                                continue;

                            if (!Paths.get(nonChart.getPath()).getParent().toFile().exists())
                                Paths.get(nonChart.getPath()).getParent().toFile().mkdirs();

                            System.out.println(nonChart.getOriginalPath() + " --> " + nonChart.getPath());
                            Files.move(Paths.get(nonChart.getOriginalPath()), Paths.get(nonChart.getPath()), StandardCopyOption.REPLACE_EXISTING);
                        } catch (Exception e) {
                            mergedSong.hadErrorMoving = true;
                            e.printStackTrace();
                        }
                    }
                } catch (Exception e) {
                    mergedSong.hadErrorMoving = true;
                    e.printStackTrace();
                }
                try {
                    for (Song song : mergedSong.getConsumedSongs()) {
                        try {
                            File dir = new File(song.getPath());
                            if (dir.listFiles().length == 0) {
                                dir.delete();
                            }
                        } catch (Exception e) {
                        }
                    }
                } catch (Exception e) {
                }
            }

            for (MergedSong mergedSong : mergedSongs) {
                if (!mergedSong.hadErrorMoving) {
                    for (Song song : mergedSong.getConsumedSongs()) {
                        File dir = new File(song.getPath());
                        try {
                            if (!dir.exists())
                                continue;

                            if (dir.listFiles().length == 0) {
                                dir.delete();
                                continue;
                            }
                        } catch (Exception e) {
                        }

                        for (Chart chart : song.getCharts()) {
                            try {
                                System.out.println("x " + chart.getPath());
                                Files.deleteIfExists(Paths.get(chart.getPath()));
                            } catch (Exception e) {

                            }
                        }
                        for (NonChart nonChart : song.getNonCharts()) {
                            try {
                                System.out.println("x " + nonChart.getPath());
                                Files.deleteIfExists(Paths.get(nonChart.getPath()));
                            } catch (Exception e) {

                            }
                        }
                        try {
                            if (dir.listFiles().length == 0) {
                                dir.delete();
                                continue;
                            }
                        } catch (Exception e) {
                        }
                    }
                }
            }

            for (MergedSong mergedSong : mergedSongs) {
                if (!mergedSong.hadErrorMoving)
                    continue;
                System.err.println("Could not properly move: " + mergedSong.getPath());
            }
        }
    }

    public static void calculateMerges(String[] bmsRootDirs, String outputDir) throws IOException {
        List<Song> songs;
        if (Paths.get("bms.data").toFile().exists()) {
            songs = readSongListFromData();
            System.out.println("if you want to re-scan your bms folder(s), delete bms.data and restart this application");
        } else {
            songs = snapshotBmsDirs(bmsRootDirs);
        }

        long totalSize = 0;
        for (Song song : songs) {
            totalSize += song.calculateFileSize();
        }

        System.out.println("total size of snapshot: " + readableByteCount(totalSize));

        System.out.println("calculating merges. Don't panic if it looks stuck.");

        HashMap<String, List<Song>> duplicatesMap = calculateDuplicateMap(songs);
        //HashMap<String, List<Song>> duplicatesMap = readDuplicatesFromData();

        List<MergedSong> mergedSongs = calculateMergeSongList(duplicatesMap, outputDir);
        //List<MergedSong> mergedSongs = readMergesFromData("merges_debug.data");

        List<MergedSong> noCollisions = new ArrayList<>();

        for (Song s : songs)
            s.deleteMe = false;

        for (MergedSong mergedSong : mergedSongs) {
            if (!mergedSong.isHasConflict()) {
                noCollisions.add(mergedSong);
                for (Song consumedSong : mergedSong.getConsumedSongs()) {
                    consumedSong.deleteMe = true;
                    for (Song s : songs) {
                        if (consumedSong.getPath().equals(s.getPath())) {
                            s.deleteMe = true; // delete me if merged
                            break;
                        }
                    }
                }
            } else {
                for (Song consumedSong : mergedSong.getConsumedSongs()) {
                    consumedSong.deleteMe = false;
                    for (Song s : songs) {
                        if (consumedSong.getPath().equals(s.getPath())) {
                            s.hasCollisions = true; // set collision flag
                            break;
                        }
                    }
                }
            }
        }

        List<MergedSong> noDuplicates = new ArrayList<>();
        List<Song> collisions = new ArrayList<>();

        for (Song song : songs) {
            if (!song.deleteMe && !song.hasCollisions) {
                MergedSong mergedSong = new MergedSong(song, outputDir);
                noDuplicates.add(mergedSong);
            } else if (song.hasCollisions) {
                collisions.add(song);
            }
        }

        totalSize = 0;
        for (Song song : songs) {
            totalSize += song.calculateFileSize();
        }

        System.out.println("total size of snapshot: " + readableByteCount(totalSize));

        int counter = 0;
        for (Song song : songs) {
            if (!song.deleteMe && song.hasCollisions) {
                counter++;
            }
        }
        System.out.println("total collision count: " + counter);

        totalSize = 0;
        for (MergedSong s : noDuplicates) {
            totalSize += s.calculateFileSize();
        }
        totalSize = 0;
        for (Song s : collisions) {
            totalSize += s.calculateFileSize();
        }
        System.out.println("total size of collisions: " + readableByteCount(totalSize));
        totalSize = 0;
        for (Song s : songs) {
            totalSize += s.calculateFileSize();
        }
        for (MergedSong s : noCollisions) {
            totalSize += s.calculateFileSize();
            for (Song c : s.getConsumedSongs()) {
                totalSize -= c.calculateFileSize();
            }
        }
        System.out.println("total size of snapshot after merging all non-collisions (running \"--merge\"): " + readableByteCount(totalSize));

        List<String> lines = new ArrayList<>();
        List<MergedSong> finalMergeSongs = new ArrayList<>();
        List<MergedSong> sortedNoCollisions = noCollisions.stream().sorted((x, y) -> Integer.compare(y.getConsumedSongs().size(), x.getConsumedSongs().size())).collect(Collectors.toList());
        String separator = "...";
        for (MergedSong song : sortedNoCollisions) {
            finalMergeSongs.add(song);
            lines.add(song + separator + song.getConsumedSongs().get(0));

            StringBuilder stride = new StringBuilder();

            for (int i = 0; i < song.toString().length(); i++)
                stride.append(".");
            stride.append(separator);

            for (int i = 1; i < song.getConsumedSongs().size(); i++) {
                lines.add(stride.toString() + song.getConsumedSongs().get(i));
            }
            lines.add("");
        }
        for (MergedSong song : noDuplicates) {
            finalMergeSongs.add(song);
            lines.add(song + separator + song.getConsumedSongs().get(0));
            lines.add("");
        }
        if (lines.size() > 0)
            lines.remove(lines.size() - 1);
        Files.write(Paths.get("merges.txt"), lines, StandardCharsets.UTF_8);
        lines.clear();
        for (MergedSong song : mergedSongs.stream().filter(MergedSong::isHasConflict).collect(Collectors.toList())) {
            lines.add(song.getPath() + separator + song.getConsumedSongs().get(0));

            StringBuilder stride = new StringBuilder();

            for (int i = 0; i < song.getPath().length(); i++)
                stride.append(".");
            stride.append(separator);

            for (int i = 1; i < song.getConsumedSongs().size(); i++) {
                lines.add(stride.toString() + song.getConsumedSongs().get(i));
            }
            lines.add("");
        }
        Files.write(Paths.get("collisions.txt"), lines, StandardCharsets.UTF_8);

        writeMergesToData(finalMergeSongs, "merges.data");

        System.out.println("merges.txt and collisions.txt generated");
        System.out.println("you can now merge with \"--merge\"");
    }

    private static List<MergedSong> calculateMergeSongList(HashMap<String, List<Song>> duplicatesMap, String outputDir) throws IOException {
        List<MergedSong> mergedSongs = new ArrayList<>();
        for (String md5 : duplicatesMap.keySet()) {
            for (Song song : duplicatesMap.get(md5)) {
                // skip if song has been merged already
                if (song.hasBeenMerged)
                    continue;

                boolean found = false;
                for (MergedSong mergedSong : mergedSongs) {
                    if (song.hasCommonMd5With(mergedSong)) {

                        // merge song --> mergedSong
                        mergedSong.consume(song);
                        song.hasBeenMerged = true;
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    MergedSong mergedSong = new MergedSong("");
                    mergedSong.consume(song);
                    mergedSongs.add(mergedSong);
                    song.hasBeenMerged = true;
                }
            }
        }

        for (MergedSong mergedSong : mergedSongs) {
            try {
                mergedSong.setPath(Paths.get(outputDir).resolve(mergedSong.calculateFolderName()).toAbsolutePath().toString());
            } catch (Exception e) {
                e.printStackTrace();
                mergedSong.setPath(System.getProperty("java.io.tmpdir"));
            }

            boolean noneFound = false;
            while (!noneFound) {
                noneFound = true;
                for (int i = 0; i < mergedSongs.indexOf(mergedSong); i++) {
                    if (mergedSongs.get(i).getPath().equals(mergedSong.getPath())) {
                        noneFound = false;
                        mergedSong.setPath(mergedSong.getPath() + "_");
                        System.err.println("fixing path");
                    }
                }
            }
            mergedSong.updatePaths();
        }

        //writeMergesToData(mergedSongs, "merges_debug.data");

        return mergedSongs;
    }

    private static HashMap<String, List<Song>> calculateDuplicateMap(List<Song> songs) throws IOException {
        HashMap<String, List<Song>> md5SongsMap = new HashMap<>();

        for (int i = 0; i < songs.size(); i++) {
            Song a = songs.get(i);
            for (int j = i + 1; j < songs.size(); j++) {
                Song b = songs.get(j);

                if (a == b)
                    continue;

                if (a.hasCommonMd5With(b)) {
                    for (Chart chart : a.getCharts()) {
                        List<Song> list = md5SongsMap.getOrDefault(chart.getMd5(), new ArrayList<>());
                        if (!list.contains(a)) {
                            list.add(a);
                        }
                        md5SongsMap.put(chart.getMd5(), list);
                    }
                    for (Chart chart : b.getCharts()) {
                        List<Song> list = md5SongsMap.getOrDefault(chart.getMd5(), new ArrayList<>());
                        if (!list.contains(b)) {
                            list.add(b);
                        }
                        md5SongsMap.put(chart.getMd5(), list);
                    }
                }
            }
        }

        //writeDuplicatesToData(md5SongsMap, "duplicates.data");

        return md5SongsMap;
    }

    /**
     * @param bmsRootDirs all bms root dirs to read from
     * @throws IOException when data could not be written
     */
    private static List<Song> snapshotBmsDirs(String[] bmsRootDirs) throws IOException {
        // check for subfolders
        List<Path> bmsRootDirPaths = new ArrayList<>();
        for (String bmsRootDir : bmsRootDirs) {
            Path bmsRootDirPath = Paths.get(bmsRootDir);
            for (Path checkedPath : bmsRootDirPaths) {

                if (checkedPath.startsWith(bmsRootDirPath) || bmsRootDirPath.startsWith(checkedPath)) {
                    throw new RuntimeException("subfolder in bms root dirs detected: " + bmsRootDir + " - " + checkedPath);
                }
            }
            bmsRootDirPaths.add(bmsRootDirPath);
        }

        List<Song> allSongs = new ArrayList<>();

        for (Path bmsRootDirPath : bmsRootDirPaths) {
            System.out.println("scanning your bms folder(s). Don't panic if it looks stuck. This might take a while");
            Stream<Path> stream = Files.find(bmsRootDirPath, Integer.MAX_VALUE, (filePath, fileAttr) -> fileAttr.isRegularFile());

            final Path[] lastPrintedSong = { null };
            final Path[] currentSongParent = { null };
            final Song[] currentSong = { null };
            // sort stream
            stream.sorted((p1, p2) -> {
                if (p1.getParent().equals(p2.getParent())) {
                    if (isBmsFile(p1) && isBmsFile(p2)) {
                        return p1.getFileName().toString().compareTo(p2.getFileName().toString());
                    }

                    if (isBmsFile(p1))
                        return -1;
                    else if (isBmsFile(p2))
                        return 1;
                }

                return p1.getParent().compareTo(p2.getParent());
            }).forEach(path -> {
                if (currentSong[0] == null)
                    currentSong[0] = new Song(path.getParent().toAbsolutePath().toString());
                Path parent = path.getParent();
                if (currentSongParent[0] == null) {
                    currentSongParent[0] = parent;
                }

                if (lastPrintedSong[0] != currentSongParent[0]) {
                    System.out.println(currentSongParent[0]);
                    lastPrintedSong[0] = currentSongParent[0];
                }

                if (isBmsFile(path)) {
                    try {
                        MessageDigest md5digest = MessageDigest.getInstance("MD5");
                        //ByteSource byteSource = com.google.common.io.Files.asByteSource(path.toFile());
                        //String md5 = byteSource.hash(Hashing.md5()).toString();
                        String title = "Unknown Title";
                        String artist = "Unknown Artist";

                        if (isBmsonFile(path)) {
                            boolean titleRead = false;
                            boolean artistRead = false;

                            try (BufferedReader br = new BufferedReader(
                                    new InputStreamReader(
                                            new DigestInputStream(
                                                    new ByteArrayInputStream(
                                                            Files.readAllBytes(path)), md5digest), Charset.forName("shift_jis")))) {
                                for (String line; (line = br.readLine()) != null; ) {
                                    if (titleRead && artistRead)
                                        continue;

                                    if (!titleRead) {
                                        if (line.matches(".*\"title\" *: *\".*")) {
                                            String temp = line.replaceAll(".*\"title\" *: *\"", "");
                                            title = temp.substring(0, temp.indexOf("\""));
                                            titleRead = true;
                                        }
                                    }
                                    if (!artistRead) {
                                        if (line.matches(".*\"artist\" *: *\".*")) {
                                            String temp = line.replaceAll(".*\"artist\" *: *\"", "");
                                            artist = temp.substring(0, temp.indexOf("\""));
                                            artistRead = true;
                                        }
                                    }
                                }
                            }

                        } else {
                            // file is in bms format
                            // extract title and artist
                            boolean titleRead = false;
                            boolean artistRead = false;

                            try (BufferedReader br = new BufferedReader(
                                    new InputStreamReader(
                                            new DigestInputStream(
                                                    new ByteArrayInputStream(
                                                            Files.readAllBytes(path)), md5digest), Charset.forName("shift_jis")))) {
                                for (String line; (line = br.readLine()) != null; ) {
                                    if (titleRead && artistRead)
                                        continue;

                                    if (!titleRead && line.startsWith("#TITLE ")) {
                                        title = line.substring("#TITLE ".length());
                                        titleRead = true;
                                        continue;
                                    }
                                    if (!artistRead && line.startsWith("#ARTIST ")) {
                                        artist = line.substring("#ARTIST ".length());
                                        artistRead = true;
                                        continue;
                                    }
                                }
                            }
                        }

                        Chart chart = new Chart(convertHexString(md5digest.digest()), path.toAbsolutePath().toString(), title, artist, path.toFile().length());

                        if (!currentSong[0].hasAtLeastOneChart()) {
                            if (currentSong[0].getNonCharts().size() != 0) {
                                System.err.println(currentSong[0].getPath() + " does not contain a bms file");
                            }

                            // create a new song
                            currentSong[0] = new Song(path.getParent().toAbsolutePath().toString());
                            currentSongParent[0] = parent;
                            currentSong[0].addChart(chart);
                        } else {
                            // current song has at least one chart

                            if (currentSongParent[0].equals(parent)) {
                                // we are in the same folder as currentSong
                                // therefore this chart is a "sabun"
                                // add it
                                currentSong[0].addChart(chart);
                            } else {
                                // we are in a different folder than currentSong
                                // flush currentSong since it has at least one chart
                                allSongs.add(currentSong[0].clone());

                                // create a new currentSong and add this chart
                                currentSong[0] = new Song(path.getParent().toAbsolutePath().toString());
                                currentSongParent[0] = parent;
                                currentSong[0].addChart(chart);
                            }
                        }
                    } catch (IOException | NoSuchAlgorithmException e) {
                        e.printStackTrace();
                    }
                } else {
                    // wav, ogg, png, bmp, etc.
                    NonChart nonChart = new NonChart(path.toAbsolutePath().toString(), path.toFile().length());

                    currentSong[0].addNonChart(nonChart);
                }
            });

            // flush current song for the last current song
            if (currentSong[0] != null && currentSong[0].hasAtLeastOneChart()) {
                allSongs.add(currentSong[0].clone());
                currentSong[0] = null;
            }
        }

        writeSongListToData(allSongs, "bms.data");

        return allSongs;
    }

    /**
     * @throws IOException when data does not exist
     */
    private static List<Song> readSongListFromData() throws IOException {
        Path path = Paths.get("bms.data");
        BasicFileAttributes attr = Files.readAttributes(path, BasicFileAttributes.class);

        System.out.println("bms.data" + ": reading bms snapshot from " + attr.creationTime());
        List<Song> songs = new ArrayList<>();
        try (ObjectInputStream ois = new ObjectInputStream(new BufferedInputStream(Files.newInputStream(path)))) {
            int songCount = ois.readInt();
            for (int i = 0; i < songCount; i++) {
                Song song = (Song) ois.readObject();
                songs.add(song);
            }
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        } catch (InvalidClassException e) {
            throw new RuntimeException("bms.data" + " version is invalid. Rescan.");
        }
        System.out.println("bms.data" + ": read " + songs.size() + " songs");
        return songs;
    }

    private static HashMap<String, List<Song>> readDuplicatesFromData() throws IOException {
        System.out.println("duplicates.data: reading duplicate md5s");
        HashMap<String, List<Song>> map;
        try (ObjectInputStream ois = new ObjectInputStream(new BufferedInputStream(Files.newInputStream(Paths.get("duplicates.data"))))) {
            map = (HashMap<String, List<Song>>) ois.readObject();
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        } catch (InvalidClassException e) {
            throw new RuntimeException("duplicates.data version is invalid. Rescan.");
        }
        System.out.println("duplicates.data: read " + map.keySet().size() + " duplicate md5s");
        return map;
    }

    private static List<MergedSong> readMergesFromData(String fileName) {
        System.out.println(fileName + ": reading duplicate md5s");
        List<MergedSong> list = new ArrayList<>();
        try (ObjectInputStream ois = new ObjectInputStream(new BufferedInputStream(Files.newInputStream(Paths.get(fileName))))) {
            int songCount = ois.readInt();
            for (int i = 0; i < songCount; i++)
                list.add((MergedSong) ois.readObject());
        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException(fileName + " version is invalid. Rescan.");
        }
        System.out.println(fileName + ": read " + list.size() + " merges");
        return list;
    }

    /**
     * @param songs    songs collection
     * @param fileName file name to write to
     * @throws IOException
     */
    private static void writeSongListToData(List<Song> songs, String fileName) throws IOException {
        System.out.println(fileName + ": writings songs");
        try (ObjectOutputStream oos = new ObjectOutputStream(new BufferedOutputStream(Files.newOutputStream(new File(fileName).toPath())))) {
            oos.writeInt(songs.size());
            for (Song song : songs) {
                oos.writeObject(song);
            }
        }
        System.out.println(fileName + ": wrote " + songs.size() + " songs");
    }

    private static void writeDuplicatesToData(HashMap<String, List<Song>> duplicates, String fileName) throws IOException {
        System.out.println(fileName + ": writings duplicate md5s");
        try (ObjectOutputStream oos = new ObjectOutputStream(new BufferedOutputStream(Files.newOutputStream(new File(fileName).toPath())))) {
            oos.writeObject(duplicates);
        }
        System.out.println(fileName + ": wrote " + duplicates.keySet().size() + " duplicate md5s");
    }

    private static void writeMergesToData(List<MergedSong> songs, String fileName) throws IOException {
        System.out.println(fileName + ": writings merges");
        try (ObjectOutputStream oos = new ObjectOutputStream(new BufferedOutputStream(Files.newOutputStream(new File(fileName).toPath())))) {
            oos.writeInt(songs.size());
            for (MergedSong song : songs) {
                oos.writeObject(song);
            }
        }
        System.out.println(fileName + ": wrote " + songs.size() + " merges");
    }

    /**
     * @param path file path
     * @return true if the file ends with .bms .bme .bml .pms .bmson
     */
    private static boolean isBmsFile(Path path) {
        return path.toString().endsWith(".bms") ||
               path.toString().endsWith(".bme") ||
               path.toString().endsWith(".bml") ||
               path.toString().endsWith(".pms") ||
               path.toString().endsWith(".bmson");
    }

    /**
     * @param path file path
     * @return true if the file ends with .bmson
     */
    private static boolean isBmsonFile(Path path) {
        return path.toString().endsWith(".bmson");
    }

    public static String readableByteCount(long bytes) {
        long absB = bytes == Long.MIN_VALUE ? Long.MAX_VALUE : Math.abs(bytes);
        if (absB < 1024) {
            return bytes + " B";
        }
        long value = absB;
        CharacterIterator ci = new StringCharacterIterator("KMGTPE");
        for (int i = 40; i >= 0 && absB > 0xfffccccccccccccL >> i; i -= 10) {
            value >>= 10;
            ci.next();
        }
        value *= Long.signum(bytes);
        String gb = String.format("%.2f %ciB", value / 1024.0, ci.current());
        String b = NumberFormat.getInstance(Locale.US).format(bytes).replaceAll(",", ".");
        return gb + " (" + b + " bytes)";
    }

    public static String convertHexString(byte[] data) {
        final StringBuilder sb = new StringBuilder(data.length * 2);
        for (byte b : data) {
            sb.append(Character.forDigit(b >> 4 & 0xf, 16));
            sb.append(Character.forDigit(b & 0xf, 16));
        }
        return sb.toString();
    }
}
