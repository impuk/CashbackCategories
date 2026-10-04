package com.example.cashback.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "banks")
data class BankEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Общий порядок банков, действует во всех категориях. */
    val position: Int,
)

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Порядок (приоритет) категорий на главном экране. */
    val position: Int,
)

/** Постоянный кэшбэк пары «банк — категория»: действует в каждом месяце. */
@Entity(
    tableName = "permanent_rates",
    primaryKeys = ["bankId", "categoryId"],
    foreignKeys = [
        ForeignKey(entity = BankEntity::class, parentColumns = ["id"], childColumns = ["bankId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("categoryId")],
)
data class PermanentRateEntity(
    val bankId: Long,
    val categoryId: Long,
    /** Процент в десятых долях: 15 = 1,5%. */
    val percentTenths: Int,
)

/** Кэшбэк, выбранный на конкретный месяц. Перекрывает постоянный в этом месяце. */
@Entity(
    tableName = "monthly_rates",
    primaryKeys = ["bankId", "categoryId", "month"],
    foreignKeys = [
        ForeignKey(entity = BankEntity::class, parentColumns = ["id"], childColumns = ["bankId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("categoryId"), Index("month")],
)
data class MonthlyRateEntity(
    val bankId: Long,
    val categoryId: Long,
    /** Ключ месяца: год * 12 + (месяц - 1), см. [com.example.cashback.domain.Months]. */
    val month: Int,
    /** Процент в десятых долях: 15 = 1,5%. */
    val percentTenths: Int,
)
