package com.example.floatinglyricforthirdpartymusicapp.db

import androidx.room.*

@Dao
interface LyricDAO {

    @Query("SELECT * FROM lyric_table")
    fun getAll(): List<Lyric>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(lyric: Lyric)

    @Delete
    fun delete(lyric: Lyric)

    @Query("DELETE FROM lyric_table")
    fun deleteAll()

}