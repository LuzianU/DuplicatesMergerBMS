# DuplicatesMergerBMS

The goal of this application is to merge your whole BMS library, containing tons of duplicates, into a single folder, with the individual song folders named "[Artist] Title"

# How does this work
Since there can go plenty wrong when merging possibly hundreds of thousands of files, there are two stages to this. Files will **only** get modified in the second stage, so if you are unsure if this will completely mess up your BMS library, you can run the first stage without any fear and see how much space all these duplicates take.

## First stage:
`java -jar DuplicatesMergerBMS.jar --calc [folder to merge to] [semicolon separated input folder list]`

<sup>(console command example: `java -jar DuplicatesMergerBMS.jar --calc "D:\BMS Merges" "D:\BMS\GENOSIDE 2018;D:\BMS\sp_insane"`)</sup>

The first stage consists of taking a snapshot of your whole BMS library and calculating all merges on this snapshot, again, without touching **any** files in the process.</br>
Since this snapshotting takes by far the most time, the snapshot will be saved in a file `bms.data`. If this file already exists, it will skip the snapshotting process entirely and read from it.

After obtaining the snapshot, the merging calculation starts. Two song folders will get merged (again, not the actual files yet) if:
- they contain a common chart (md5) and</br>
- all the .wav and .ogg audio files with the same name have the exact same file size

A merge conflict happens if any of the audio files have different file sizes

After all merge calculations are done `merges.data` will be created which will be used in the second stage where the actual files will get modified.

Also `merges.txt` and `collisions.txt` will be generated with `merges.txt` [displaying which folders are merged with which and how the actual output will look like](https://github.com/LuzianU/DuplicatesMergerBMS/blob/main/example_merges.txt) (This .txt file is just a visualization editing the paths in it will not do anything)


## Second stage:
`java -jar DuplicatesMergerBMS.jar --merge`

Only now your files will get modified.

After looking at the generated `merges.txt` file and wanting to have your library merged like the visualization shows, you can run this application with the argument `--merge` as shown above.</br>

After the file moving process is done, all the merges without conflicts (so all shown in the `merges.txt` file) will be moved from your input folder list to the output folder. The only song folders left in your input folder list are song folders with merge conflicts. You will have to sort them out manually, however as you saw in the `conflicts.txt` file, this amount should be quite low.

There might be some additional empty folders which you can all delete with [this](https://superuser.com/a/39679) command

# Requirements
- Java version 8 or above
