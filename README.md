# FloatingLyricForMOOV

## What is this project?
This app perform runtime tracking of the music info (title, author, progress etc.) that is playing by the Music App: [MOOV](https://moov.hk/), which is a subscription-based music service. Then, display the lyric on screen and overlaying other apps.

App demo:

![QtScrcpy_20210905_022556_462](https://user-images.githubusercontent.com/54059281/132105903-6a1c1fd1-da62-4214-b1a7-daa53a4d7cf1.gif)


## How does this app tracks the music info?
It extract info from the MOOV music player in status bar notification as below:
![InkedQtScrcpy_20210905_024656_207_LI](https://user-images.githubusercontent.com/54059281/132105399-88b80fa4-085c-4d74-be2d-a200374da04e.jpg)

To be more specific, this app is obtaining the [MediaSession token](https://developer.android.google.cn/guide/topics/media/media-controls?hl=zh-cn) of MOOV's notification. Meanwhile, that MediaSession token would allow us to access MOOV MediaSession's MediaMetadata, which contains the following information about the playing music:

*- METADATA_KEY_ALBUM_ART_URI*

***- METADATA_KEY_TITLE***

***- METADATA_KEY_ARTIST***

***- METADATA_KEY_DURATION***

## Is the displaying lyric obtained online?
Yes and no.
Yes: the lyric content are obtained on Internet, but not obtained in runtime.
No: the lyric content are stored as .LRC file in local storage: /storage/emulated/0/Music/
