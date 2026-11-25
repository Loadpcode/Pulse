package com.xraypulse.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.xraypulse.app.model.RuleAction
import com.xraypulse.app.model.RuleType
import com.xraypulse.app.model.SecurityType
import com.xraypulse.app.model.TransportType
import com.xraypulse.app.model.VpnProtocol

class VpnTypeConverters {
    @TypeConverter fun fromProtocol(v: VpnProtocol): String = v.name
    @TypeConverter fun toProtocol(v: String): VpnProtocol = VpnProtocol.valueOf(v)

    @TypeConverter fun fromTransport(v: TransportType): String = v.name
    @TypeConverter fun toTransport(v: String): TransportType = TransportType.valueOf(v)

    @TypeConverter fun fromSecurity(v: SecurityType): String = v.name
    @TypeConverter fun toSecurity(v: String): SecurityType = SecurityType.valueOf(v)

    @TypeConverter fun fromRuleType(v: RuleType): String = v.name
    @TypeConverter fun toRuleType(v: String): RuleType = RuleType.valueOf(v)

    @TypeConverter fun fromRuleAction(v: RuleAction): String = v.name
    @TypeConverter fun toRuleAction(v: String): RuleAction = RuleAction.valueOf(v)
}

@Database(
    entities = [
        ServerProfileEntity::class,
        SubscriptionEntity::class,
        RoutingRuleEntity::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(VpnTypeConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun serverProfileDao(): ServerProfileDao
    abstract fun subscriptionDao(): SubscriptionDao
    abstract fun routingRuleDao(): RoutingRuleDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE server_profiles ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "xray_pulse.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { instance = it }
            }
        }
    }
}
