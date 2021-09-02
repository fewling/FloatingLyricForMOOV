package com.example.floatinglyricforthirdpartymusicapp.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "lyric_table")
class Lyric(
    @PrimaryKey @ColumnInfo(name = "lyric_path") var lyric_path: String,
    @ColumnInfo(name = "lyric_file_name") var lyric_file_name: String
)