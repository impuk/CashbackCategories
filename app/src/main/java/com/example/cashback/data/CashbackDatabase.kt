package com.example.cashback.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [BankEntity::class, CategoryEntity::class, PermanentRateEntity::class, MonthlyRateEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class CashbackDatabase : RoomDatabase() {
    abstract fun dao(): CashbackDao

    companion object {
        const val NAME = "cashback.db"

        fun build(context: Context): CashbackDatabase =
            Room.databaseBuilder(context, CashbackDatabase::class.java, NAME)
                .addCallback(SeedCallback)
                .build()

        fun inMemory(context: Context): CashbackDatabase =
            Room.inMemoryDatabaseBuilder(context, CashbackDatabase::class.java)
                .addCallback(SeedCallback)
                .allowMainThreadQueries()
                .build()
    }

    /** Кладёт готовые банки и категории при создании базы. */
    object SeedCallback : Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            DefaultData.banks.forEachIndexed { index, name ->
                db.execSQL("INSERT INTO banks (name, position) VALUES (?, ?)", arrayOf<Any>(name, index))
            }
            DefaultData.categories.forEachIndexed { index, name ->
                db.execSQL("INSERT INTO categories (name, position) VALUES (?, ?)", arrayOf<Any>(name, index))
            }
        }
    }
}
