package com.cleanincome.android;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

public class IncomeDb extends SQLiteOpenHelper {
    public static final String DB_NAME = "clean_income_native.db";
    private static final int DB_VERSION = 2;

    public IncomeDb(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE jobs (" +
                "id TEXT PRIMARY KEY, name TEXT NOT NULL, type TEXT NOT NULL, defaultHourlyRate REAL, " +
                "defaultFixedAmount REAL, color TEXT NOT NULL, currency TEXT NOT NULL, isArchived INTEGER NOT NULL, " +
                "createdAt TEXT NOT NULL, updatedAt TEXT NOT NULL)");
        db.execSQL("CREATE TABLE shifts (" +
                "id TEXT PRIMARY KEY, jobId TEXT NOT NULL, date TEXT NOT NULL, startDateTime TEXT NOT NULL, " +
                "endDateTime TEXT NOT NULL, breakMinutes INTEGER NOT NULL, isBreakPaid INTEGER NOT NULL, " +
                "paymentType TEXT NOT NULL, hourlyRate REAL, fixedAmount REAL, grossAmountManual REAL, " +
                "ordersCount INTEGER NOT NULL DEFAULT 0, shiftExpenseAmount REAL NOT NULL DEFAULT 0, " +
                "tips REAL NOT NULL, bonus REAL NOT NULL, penalty REAL NOT NULL, note TEXT NOT NULL, " +
                "createdAt TEXT NOT NULL, updatedAt TEXT NOT NULL)");
        db.execSQL("CREATE TABLE expenses (" +
                "id TEXT PRIMARY KEY, jobId TEXT, shiftId TEXT, date TEXT NOT NULL, amount REAL NOT NULL, " +
                "category TEXT NOT NULL, note TEXT NOT NULL, createdAt TEXT NOT NULL, updatedAt TEXT NOT NULL)");
        db.execSQL("CREATE TABLE payouts (" +
                "id TEXT PRIMARY KEY, jobId TEXT, date TEXT NOT NULL, amount REAL NOT NULL, type TEXT NOT NULL, " +
                "note TEXT NOT NULL, createdAt TEXT NOT NULL, updatedAt TEXT NOT NULL)");
        db.execSQL("CREATE TABLE goals (" +
                "id TEXT PRIMARY KEY, periodType TEXT NOT NULL, targetAmount REAL NOT NULL, startDate TEXT NOT NULL, " +
                "endDate TEXT NOT NULL, isActive INTEGER NOT NULL, createdAt TEXT NOT NULL, updatedAt TEXT NOT NULL)");
        db.execSQL("CREATE TABLE settings (" +
                "id TEXT PRIMARY KEY, currency TEXT NOT NULL, themeMode TEXT NOT NULL, weekStartDay INTEGER NOT NULL, " +
                "defaultHomePeriod TEXT NOT NULL, showGrossCard INTEGER NOT NULL, showExpensesCard INTEGER NOT NULL, " +
                "showNetCard INTEGER NOT NULL, showRemainingCard INTEGER NOT NULL, showNetPerHourCard INTEGER NOT NULL, " +
                "showGoalCard INTEGER NOT NULL, showRecentActivity INTEGER NOT NULL, homeCardOrder TEXT NOT NULL, " +
                "remainingFormulaMode TEXT NOT NULL, roundingMode TEXT NOT NULL, notificationsEnabled INTEGER NOT NULL, " +
                "hasCompletedOnboarding INTEGER NOT NULL, homeCompactMode INTEGER NOT NULL)");
        insertDefaultSettings(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            safeAlter(db, "ALTER TABLE shifts ADD COLUMN ordersCount INTEGER NOT NULL DEFAULT 0");
            safeAlter(db, "ALTER TABLE shifts ADD COLUMN shiftExpenseAmount REAL NOT NULL DEFAULT 0");
        }
    }

    public void ensureSeed() {
        SQLiteDatabase db = getWritableDatabase();
        Cursor settings = db.rawQuery("SELECT id FROM settings WHERE id='settings'", null);
        boolean hasSettings = settings.moveToFirst();
        settings.close();
        if (!hasSettings) {
            insertDefaultSettings(db);
        }
    }

    public void finishOnboarding(String jobName) {
        SQLiteDatabase db = getWritableDatabase();
        Cursor jobs = db.rawQuery("SELECT id FROM jobs LIMIT 1", null);
        boolean hasJob = jobs.moveToFirst();
        jobs.close();
        if (!hasJob) {
            ContentValues job = new ContentValues();
            String time = now();
            job.put("id", id("job"));
            job.put("name", jobName == null || jobName.trim().isEmpty() ? "Моя работа" : jobName.trim());
            job.put("type", "hourly");
            job.put("defaultHourlyRate", 350);
            job.putNull("defaultFixedAmount");
            job.put("color", "#14B87A");
            job.put("currency", "RUB");
            job.put("isArchived", 0);
            job.put("createdAt", time);
            job.put("updatedAt", time);
            db.insert("jobs", null, job);
        }

        Cursor goals = db.rawQuery("SELECT id FROM goals LIMIT 1", null);
        boolean hasGoal = goals.moveToFirst();
        goals.close();
        if (!hasGoal) {
            LocalDate today = LocalDate.now();
            ContentValues goal = new ContentValues();
            String time = now();
            goal.put("id", id("goal"));
            goal.put("periodType", "month");
            goal.put("targetAmount", 80000);
            goal.put("startDate", today.withDayOfMonth(1).format(DateTimeFormatter.ISO_LOCAL_DATE));
            goal.put("endDate", today.withDayOfMonth(today.lengthOfMonth()).format(DateTimeFormatter.ISO_LOCAL_DATE));
            goal.put("isActive", 1);
            goal.put("createdAt", time);
            goal.put("updatedAt", time);
            db.insert("goals", null, goal);
        }

        ContentValues values = new ContentValues();
        values.put("hasCompletedOnboarding", 1);
        db.update("settings", values, "id='settings'", null);
    }

    public void clearFinancialData() {
        SQLiteDatabase db = getWritableDatabase();
        db.delete("shifts", null, null);
        db.delete("expenses", null, null);
        db.delete("payouts", null, null);
        db.delete("goals", null, null);
    }

    private void insertDefaultSettings(SQLiteDatabase db) {
        ContentValues values = new ContentValues();
        values.put("id", "settings");
        values.put("currency", "RUB");
        values.put("themeMode", "light");
        values.put("weekStartDay", 1);
        values.put("defaultHomePeriod", "month");
        values.put("showGrossCard", 1);
        values.put("showExpensesCard", 1);
        values.put("showNetCard", 1);
        values.put("showRemainingCard", 1);
        values.put("showNetPerHourCard", 1);
        values.put("showGoalCard", 1);
        values.put("showRecentActivity", 1);
        values.put("homeCardOrder", "net,gross,expenses,remaining,netPerHour,goal");
        values.put("remainingFormulaMode", "gross_minus_payouts");
        values.put("roundingMode", "none");
        values.put("notificationsEnabled", 0);
        values.put("hasCompletedOnboarding", 0);
        values.put("homeCompactMode", 0);
        db.insertWithOnConflict("settings", null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    private void safeAlter(SQLiteDatabase db, String sql) {
        try {
            db.execSQL(sql);
        } catch (Exception ignored) {
        }
    }

    public static String id(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "");
    }

    public static String now() {
        return java.time.LocalDateTime.now().toString();
    }
}
