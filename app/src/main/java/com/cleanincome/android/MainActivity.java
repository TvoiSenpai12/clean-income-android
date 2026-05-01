package com.cleanincome.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Currency;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends Activity {
    private static final int C_INK = Color.rgb(16, 24, 40);
    private static final int C_TEXT = Color.rgb(71, 84, 103);
    private static final int C_MUTED = Color.rgb(102, 112, 133);
    private static final int C_BORDER = Color.rgb(234, 236, 240);
    private static final int C_BG = Color.rgb(248, 250, 252);
    private static final int C_SURFACE = Color.WHITE;
    private static final int C_PRIMARY = Color.rgb(20, 184, 122);
    private static final int C_PRIMARY_DARK = Color.rgb(15, 163, 107);
    private static final int C_PRIMARY_SOFT = Color.rgb(221, 247, 236);
    private static final int C_TEAL = Color.rgb(15, 118, 110);
    private static final int C_NEGATIVE = Color.rgb(226, 85, 85);
    private static final int C_NEGATIVE_SOFT = Color.rgb(253, 232, 232);
    private static final int C_WARNING = Color.rgb(245, 158, 11);
    private static final int C_WARNING_SOFT = Color.rgb(254, 243, 199);
    private static final int REQ_EXPORT_CSV = 10;
    private static final int REQ_EXPORT_JSON = 11;
    private static final int REQ_IMPORT_JSON = 12;
    private static final String TAG = "CleanIncome";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private IncomeDb helper;
    private SQLiteDatabase db;
    private Settings settings;
    private String historyPeriod = "month";
    private String historyKind = "all";
    private boolean screenTransitioning = false;

    interface ValueCallback {
        void call(String value);
    }

    interface ClickAction {
        void run();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        helper = new IncomeDb(this);
        helper.ensureSeed();
        db = helper.getWritableDatabase();
        loadSettings();
        if (!settings.hasCompletedOnboarding) {
            showOnboarding();
        } else {
            showHome();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        try {
            Uri uri = data.getData();
            if (requestCode == REQ_EXPORT_CSV) {
                writeUri(uri, buildCsv());
                toast("CSV сохранен");
            } else if (requestCode == REQ_EXPORT_JSON) {
                writeUri(uri, buildBackupJson());
                toast("Резервная копия сохранена");
            } else if (requestCode == REQ_IMPORT_JSON) {
                importBackupJson(readUri(uri));
                loadSettings();
                toast("Импорт готов");
                showHome();
            }
        } catch (Exception e) {
            toast("Ошибка файла: " + e.getMessage());
        }
    }

    private void showOnboarding() {
        LinearLayout root = vertical();
        root.setPadding(dp(22), dp(42), dp(22), dp(22));
        root.setGravity(Gravity.CENTER_VERTICAL);
        root.setBackgroundColor(C_BG);

        TextView logo = text("✓↗", 38, Color.WHITE, true);
        logo.setGravity(Gravity.CENTER);
        logo.setBackground(round(C_PRIMARY, dp(22), C_PRIMARY, 0));
        root.addView(logo, box(dp(78), dp(78)));

        TextView title = text("Чистый доход", 34, C_INK, true);
        title.setGravity(Gravity.CENTER);
        root.addView(title, matchWrapTop(dp(18)));

        TextView sub = text("Личный учет смен, подработок и выплат. Без аккаунта, без сервера, все локально на телефоне.", 16, C_MUTED, false);
        sub.setGravity(Gravity.CENTER);
        sub.setLineSpacing(0, 1.15f);
        root.addView(sub, matchWrapTop(dp(10)));

        LinearLayout card = card();
        card.addView(text("• Чистыми за 3 секунды после открытия", 15, C_TEXT, false));
        card.addView(text("• Расходы, выплаты и остаток к получению", 15, C_TEXT, false));
        card.addView(text("• Цель дохода и чистыми в час", 15, C_TEXT, false));
        root.addView(card, matchWrapTop(dp(22)));

        EditText jobName = input("Моя работа", "Первая работа", InputType.TYPE_CLASS_TEXT);
        root.addView(labeled("Первая работа", jobName), matchWrapTop(dp(18)));
        root.addView(button("Начать учет", C_PRIMARY, Color.WHITE, () -> {
            helper.finishOnboarding(jobName.getText().toString());
            loadSettings();
            showHome();
        }), matchWrapTop(dp(18)));
        setContentView(root);
    }

    private void showHome() {
        loadSettings();
        Range range = range(settings.defaultHomePeriod);
        Summary summary = summary(range);
        LinearLayout body = vertical();
        body.addView(periodButtons(settings.defaultHomePeriod, value -> {
            updateSetting("defaultHomePeriod", value);
            showHome();
        }), matchWrap());

        LinearLayout hero = card();
        hero.setBackground(round(C_INK, dp(20), C_INK, 0));
        hero.addView(text("Чистыми за период", 14, Color.rgb(204, 251, 241), true));
        hero.addView(text(money(summary.net, true), 40, Color.WHITE, true));
        hero.addView(text("Осталось получить " + money(summary.remaining, true) + " · " + one(summary.hours) + " ч", 13, Color.rgb(204, 251, 241), true));
        body.addView(hero, matchWrapTop(dp(14)));

        String[] order = settings.homeCardOrder.split(",");
        for (String cardId : order) {
            if (cardId.equals("net") && settings.showNetCard) body.addView(metric("Чистый доход", money(summary.net, true), "Начислено минус расходы", C_PRIMARY_SOFT), matchWrapTop(dp(12)));
            if (cardId.equals("gross") && settings.showGrossCard) body.addView(metric("Начислено", money(summary.gross, true), summary.shiftCount + " смен", Color.rgb(204, 251, 241)), matchWrapTop(dp(12)));
            if (cardId.equals("expenses") && settings.showExpensesCard) body.addView(metric("Расходы", money(summary.expenses, true), "Все категории", C_NEGATIVE_SOFT), matchWrapTop(dp(12)));
            if (cardId.equals("remaining") && settings.showRemainingCard) body.addView(metric("Осталось получить", money(summary.remaining, true), "Выплачено " + money(summary.paid, true), C_PRIMARY_SOFT), matchWrapTop(dp(12)));
            if (cardId.equals("netPerHour") && settings.showNetPerHourCard) body.addView(metric("Чистыми в час", money(summary.netPerHour, true), summary.hours > 0 ? one(summary.hours) + " оплаченных часов" : "Пока без часов", C_WARNING_SOFT), matchWrapTop(dp(12)));
            if (cardId.equals("goal") && settings.showGoalCard && summary.goalTarget > 0) body.addView(goalCard(summary), matchWrapTop(dp(12)));
        }

        LinearLayout actions = vertical();
        actions.addView(twoButtons("Добавить смену", () -> showAdd("shift", null), "Добавить доход", () -> showAdd("income", null)), matchWrap());
        actions.addView(twoButtons("Добавить расход", () -> showAdd("expense", null), "Добавить выплату", () -> showAdd("payout", null)), matchWrapTop(dp(10)));
        body.addView(actions, matchWrapTop(dp(16)));

        if (settings.showRecentActivity) {
            LinearLayout recent = card();
            recent.addView(text("Последние записи", 18, C_INK, true));
            ArrayList<ActivityItem> items = activities(range);
            if (items.isEmpty()) {
                recent.addView(empty("Записей пока нет", "Добавьте смену, доход, расход или выплату."));
            } else {
                for (int i = 0; i < Math.min(4, items.size()); i++) {
                    recent.addView(activityRow(items.get(i), false));
                }
            }
            body.addView(recent, matchWrapTop(dp(16)));
        }

        setScreen("Чистый доход", range.label, body, "home");
    }

    private void showAdd(String kind, String editId) {
        loadSettings();
        ArrayList<Job> jobs = jobs(false);
        Prefill prefill = prefill(kind, editId, jobs);
        LinearLayout body = vertical();

        final String[] selectedKind = {prefill.kind};
        body.addView(spinner(new String[]{"Смена", "Доход", "Расход", "Выплата"}, new String[]{"shift", "income", "expense", "payout"}, selectedKind[0], value -> showAdd(value, null)), matchWrap());

        final String[] selectedJob = {prefill.jobId};
        if (!jobs.isEmpty() || selectedKind[0].equals("expense") || selectedKind[0].equals("payout")) {
            ArrayList<String> labels = new ArrayList<>();
            ArrayList<String> values = new ArrayList<>();
            if (selectedKind[0].equals("expense") || selectedKind[0].equals("payout")) {
                labels.add("Общее");
                values.add("none");
            }
            for (Job job : jobs) {
                labels.add(job.name);
                values.add(job.id);
            }
            if (selectedJob[0].isEmpty() && !values.isEmpty()) selectedJob[0] = values.get(0);
            body.addView(labeled("Работа", spinner(labels.toArray(new String[0]), values.toArray(new String[0]), selectedJob[0], value -> selectedJob[0] = value)), matchWrapTop(dp(12)));
        } else {
            body.addView(empty("Нет активных работ", "Создайте работу на экране «Еще» → «Работы»."));
        }

        EditText date = input(prefill.date, "2026-05-01", InputType.TYPE_CLASS_DATETIME);
        EditText start = input(prefill.startTime, "09:00", InputType.TYPE_CLASS_DATETIME);
        EditText end = input(prefill.endTime, "18:00", InputType.TYPE_CLASS_DATETIME);
        EditText breakMinutes = input(prefill.breakMinutes, "0", InputType.TYPE_CLASS_NUMBER);
        CheckBox breakPaid = check("Перерыв оплачивается", prefill.isBreakPaid);
        final String[] paymentType = {prefill.paymentType};
        Spinner paymentSpinner = spinner(new String[]{"Почасовая", "Фикс", "Заказы"}, new String[]{"hourly", "fixed", "orders"}, paymentType[0], value -> paymentType[0] = value);
        final String[] payoutType = {prefill.payoutType};
        Spinner payoutSpinner = spinner(new String[]{"Выплата", "Аванс", "Коррекция"}, new String[]{"payout", "advance", "correction"}, payoutType[0], value -> payoutType[0] = value);
        final String[] category = {prefill.category};
        Spinner categorySpinner = spinner(expenseLabelArray(), expenseValueArray(), category[0], value -> category[0] = value);

        EditText hourly = input(prefill.hourlyRate, "350", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        EditText fixed = input(prefill.fixedAmount, "3000", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        EditText amount = input(prefill.amount, "0", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        EditText tips = input(prefill.tips, "0", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        EditText bonus = input(prefill.bonus, "0", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        EditText penalty = input(prefill.penalty, "0", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        EditText note = input(prefill.note, "Необязательно", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        note.setMinLines(2);

        LinearLayout form = card();
        form.addView(labeled("Дата", date));
        if (selectedKind[0].equals("shift")) {
            form.addView(labeled("Тип оплаты", paymentSpinner));
            form.addView(twoFields("Начало", start, "Конец", end), matchWrapTop(dp(10)));
            form.addView(labeled("Перерыв, мин", breakMinutes), matchWrapTop(dp(10)));
            form.addView(breakPaid, matchWrapTop(dp(8)));
            form.addView(twoFields("Ставка в час", hourly, "Фикс за смену", fixed), matchWrapTop(dp(10)));
            form.addView(labeled("Сумма заказов", amount), matchWrapTop(dp(10)));
        } else if (selectedKind[0].equals("income")) {
            form.addView(labeled("Сумма дохода", amount), matchWrapTop(dp(10)));
        } else if (selectedKind[0].equals("expense")) {
            form.addView(labeled("Сумма расхода", amount), matchWrapTop(dp(10)));
            form.addView(labeled("Категория", categorySpinner), matchWrapTop(dp(10)));
        } else if (selectedKind[0].equals("payout")) {
            form.addView(labeled("Сумма выплаты", amount), matchWrapTop(dp(10)));
            form.addView(labeled("Тип выплаты", payoutSpinner), matchWrapTop(dp(10)));
        }
        if (selectedKind[0].equals("shift") || selectedKind[0].equals("income")) {
            form.addView(twoFields("Чаевые", tips, "Бонус", bonus), matchWrapTop(dp(10)));
            form.addView(labeled("Штраф", penalty), matchWrapTop(dp(10)));
        }
        form.addView(labeled("Заметка", note), matchWrapTop(dp(10)));
        form.addView(button(editId == null ? "Добавить запись" : "Сохранить изменения", C_PRIMARY, Color.WHITE, () -> {
            if ((selectedKind[0].equals("shift") || selectedKind[0].equals("income")) && (selectedJob[0] == null || selectedJob[0].isEmpty())) {
                toast("Сначала создайте работу");
                return;
            }
            saveRecord(selectedKind[0], editId, selectedJob[0], date.getText().toString(), start.getText().toString(),
                    end.getText().toString(), breakMinutes.getText().toString(), breakPaid.isChecked(), paymentType[0],
                    payoutType[0], category[0], hourly.getText().toString(), fixed.getText().toString(),
                    amount.getText().toString(), tips.getText().toString(), bonus.getText().toString(),
                    penalty.getText().toString(), note.getText().toString());
            toast(editId == null ? "Запись добавлена" : "Запись обновлена");
            showHome();
        }), matchWrapTop(dp(16)));
        body.addView(form, matchWrapTop(dp(14)));
        setScreen(editId == null ? "Добавить" : "Редактировать", "Смена, доход, расход или выплата", body, "add");
    }

    private void showHistory() {
        Range range = range(historyPeriod);
        LinearLayout body = vertical();
        body.addView(spinner(new String[]{"День", "Неделя", "Месяц", "Все"}, new String[]{"today", "week", "month", "all"}, historyPeriod, value -> {
            historyPeriod = value;
            showHistory();
        }), matchWrap());
        body.addView(spinner(new String[]{"Все", "Смены", "Доход", "Расходы", "Выплаты"}, new String[]{"all", "shift", "income", "expense", "payout"}, historyKind, value -> {
            historyKind = value;
            showHistory();
        }), matchWrapTop(dp(10)));

        LinearLayout list = card();
        ArrayList<ActivityItem> items = activities(range);
        int count = 0;
        for (ActivityItem item : items) {
            if (!historyKind.equals("all") && !historyKind.equals(item.kind)) continue;
            list.addView(activityRow(item, true));
            count++;
        }
        if (count == 0) list.addView(empty("Нет записей", "Поменяйте фильтр или добавьте запись."));
        body.addView(list, matchWrapTop(dp(14)));
        setScreen("История", "Фильтры, редактирование, дублирование и удаление", body, "history");
    }

    private void showAnalytics() {
        Range range = range("month");
        Summary s = summary(range);
        LinearLayout body = vertical();
        body.addView(twoMetrics("Чистый доход", money(s.net, true), "Выплачено", money(s.paid, true)), matchWrap());
        body.addView(twoMetrics("Смен", String.valueOf(s.shiftCount), "Часов", one(s.hours)), matchWrapTop(dp(10)));

        LinearLayout byDay = card();
        byDay.addView(text("Доход по дням", 18, C_INK, true));
        ArrayList<DayValue> days = valuesByDay(range);
        double max = 1;
        for (DayValue d : days) max = Math.max(max, Math.abs(d.net));
        if (days.isEmpty()) byDay.addView(empty("Нет данных", ""));
        for (DayValue d : days) byDay.addView(bar(shortDate(d.date), d.net, max, d.net >= 0 ? C_PRIMARY : C_NEGATIVE));
        body.addView(byDay, matchWrapTop(dp(14)));

        LinearLayout byJob = card();
        byJob.addView(text("Доход по работам", 18, C_INK, true));
        ArrayList<JobValue> jobValues = valuesByJob(range);
        double jobMax = 1;
        for (JobValue j : jobValues) jobMax = Math.max(jobMax, Math.abs(j.net));
        if (jobValues.isEmpty()) byJob.addView(empty("Нет дохода по работам", ""));
        for (JobValue j : jobValues) byJob.addView(bar(j.name, j.net, jobMax, parseColor(j.color, C_PRIMARY)));
        body.addView(byJob, matchWrapTop(dp(14)));

        LinearLayout byExpense = card();
        byExpense.addView(text("Расходы по категориям", 18, C_INK, true));
        HashMap<String, Double> cats = expensesByCategory(range);
        double expMax = 1;
        for (double v : cats.values()) expMax = Math.max(expMax, v);
        if (cats.isEmpty()) byExpense.addView(empty("Расходов нет", ""));
        for (String key : cats.keySet()) byExpense.addView(bar(expenseLabel(key), cats.get(key), expMax, C_NEGATIVE));
        body.addView(byExpense, matchWrapTop(dp(14)));

        body.addView(metric("Средний доход за смену", money(s.averageShiftNet, true), "Остаток к выплате " + money(s.remaining, true), C_PRIMARY_SOFT), matchWrapTop(dp(14)));
        setScreen("Аналитика", "Графики и сводка вынесены отдельно", body, "analytics");
    }

    private void showMore() {
        LinearLayout body = vertical();
        LinearLayout list = card();
        list.addView(navRow("Выплаты", "Начислено, выплачено и остаток", () -> showPayouts()));
        list.addView(navRow("Работы", "Ставки, цвета и архив", () -> showJobs(null)));
        list.addView(navRow("Настройки", "Карточки, экспорт, импорт и формулы", () -> showSettings()));
        body.addView(list);
        setScreen("Еще", "Редкие действия отдельно от главного экрана", body, "more");
    }

    private void showPayouts() {
        Range range = range(settings.defaultHomePeriod);
        Summary s = summary(range);
        LinearLayout body = vertical();
        body.addView(twoMetrics("Начислено", money(s.gross, true), "Выплачено", money(s.paid, true)), matchWrap());
        body.addView(metric("Осталось получить", money(s.remaining, true), "", C_WARNING_SOFT), matchWrapTop(dp(10)));
        body.addView(button("Добавить выплату", C_PRIMARY, Color.WHITE, () -> showAdd("payout", null)), matchWrapTop(dp(12)));
        LinearLayout list = card();
        Cursor c = db.rawQuery("SELECT * FROM payouts ORDER BY date DESC, createdAt DESC", null);
        if (!c.moveToFirst()) {
            list.addView(empty("Выплат пока нет", ""));
        } else {
            do {
                list.addView(simpleRow(payoutLabel(str(c, "type")), shortDate(str(c, "date")) + " · " + jobName(str(c, "jobId")), money(num(c, "amount"), true), C_TEAL));
            } while (c.moveToNext());
        }
        c.close();
        body.addView(list, matchWrapTop(dp(14)));
        setScreen("Выплаты", "Список выплат и остаток", body, "more");
    }

    private void showJobs(String editId) {
        ArrayList<Job> allJobs = jobs(true);
        Job edit = null;
        for (Job j : allJobs) if (j.id.equals(editId)) edit = j;
        LinearLayout body = vertical();
        EditText name = input(edit == null ? "" : edit.name, "Курьер, такси, кафе", InputType.TYPE_CLASS_TEXT);
        EditText rate = input(edit == null ? "" : clean(edit.hourlyRate), "350", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        EditText fixed = input(edit == null ? "" : clean(edit.fixed), "3000", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        final String[] type = {edit == null ? "hourly" : edit.type};
        final String[] color = {edit == null ? "#14B87A" : edit.color};
        LinearLayout form = card();
        form.addView(labeled("Название", name));
        form.addView(labeled("Тип расчета", spinner(new String[]{"Часы", "Фикс", "Заказы", "Разное"}, new String[]{"hourly", "fixed", "courier", "mixed"}, type[0], v -> type[0] = v)), matchWrapTop(dp(10)));
        form.addView(twoFields("Ставка", rate, "Фикс", fixed), matchWrapTop(dp(10)));
        form.addView(labeled("Цвет", spinner(new String[]{"Зеленый", "Темный зеленый", "Желтый", "Синий", "Красный"}, new String[]{"#14B87A", "#0F766E", "#F59E0B", "#4F46E5", "#E25555"}, color[0], v -> color[0] = v)), matchWrapTop(dp(10)));
        final int archivedValue = edit == null ? 0 : edit.archived;
        form.addView(button(edit == null ? "Создать работу" : "Сохранить работу", C_PRIMARY, Color.WHITE, () -> {
            saveJob(editId, name.getText().toString(), type[0], rate.getText().toString(), fixed.getText().toString(), color[0], archivedValue);
            showJobs(null);
        }), matchWrapTop(dp(14)));
        if (edit != null) form.addView(button("Отмена", C_BORDER, C_INK, () -> showJobs(null)), matchWrapTop(dp(8)));
        body.addView(form);

        LinearLayout list = card();
        for (Job j : allJobs) {
            LinearLayout row = horizontal();
            row.setGravity(Gravity.CENTER_VERTICAL);
            TextView dot = new TextView(this);
            dot.setBackground(round(parseColor(j.color, C_PRIMARY), dp(6), parseColor(j.color, C_PRIMARY), 0));
            row.addView(dot, box(dp(12), dp(12)));
            LinearLayout texts = vertical();
            texts.addView(text(j.name, 15, C_INK, true));
            texts.addView(text((j.archived == 1 ? "В архиве" : "Активна") + " · ставка " + clean(j.hourlyRate), 13, C_MUTED, false));
            row.addView(texts, weightWrap(1));
            row.addView(button("Ред.", C_BORDER, C_INK, () -> showJobs(j.id)), wrap());
            row.addView(button(j.archived == 1 ? "Вернуть" : "Архив", C_BORDER, C_INK, () -> {
                archiveJob(j.id, j.archived == 1 ? 0 : 1);
                showJobs(null);
            }), wrapLeft(dp(6)));
            list.addView(row, matchWrapTop(dp(10)));
        }
        body.addView(list, matchWrapTop(dp(14)));
        setScreen("Работы", "Ставки, тип расчета и архив", body, "more");
    }

    private void showSettings() {
        loadSettings();
        LinearLayout body = vertical();
        LinearLayout home = card();
        home.addView(text("Главный экран", 18, C_INK, true));
        home.addView(settingPeriod("Период по умолчанию", settings.defaultHomePeriod));
        home.addView(toggleRow("Компактный режим", "homeCompactMode", settings.homeCompactMode));
        home.addView(toggleRow("Последние записи", "showRecentActivity", settings.showRecentActivity));
        home.addView(toggleRow("Начислено", "showGrossCard", settings.showGrossCard));
        home.addView(toggleRow("Расходы", "showExpensesCard", settings.showExpensesCard));
        home.addView(toggleRow("Чистый доход", "showNetCard", settings.showNetCard));
        home.addView(toggleRow("Осталось получить", "showRemainingCard", settings.showRemainingCard));
        home.addView(toggleRow("Чистыми в час", "showNetPerHourCard", settings.showNetPerHourCard));
        home.addView(toggleRow("Цель", "showGoalCard", settings.showGoalCard));
        body.addView(home);

        LinearLayout order = card();
        order.addView(text("Порядок карточек", 18, C_INK, true));
        String[] cards = settings.homeCardOrder.split(",");
        for (String card : cards) {
            LinearLayout row = horizontal();
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.addView(text(cardLabel(card), 15, C_INK, true), weightWrap(1));
            row.addView(button("↑", C_BORDER, C_INK, () -> moveCard(card, -1)), box(dp(42), dp(40)));
            row.addView(button("↓", C_BORDER, C_INK, () -> moveCard(card, 1)), boxLeft(dp(42), dp(40), dp(6)));
            order.addView(row, matchWrapTop(dp(8)));
        }
        body.addView(order, matchWrapTop(dp(14)));

        LinearLayout money = card();
        money.addView(text("Деньги и расчет", 18, C_INK, true));
        money.addView(labeled("Валюта", spinner(new String[]{"RUB", "USD", "EUR", "KZT"}, new String[]{"RUB", "USD", "EUR", "KZT"}, settings.currency, v -> {
            updateSetting("currency", v);
            showSettings();
        })), matchWrapTop(dp(10)));
        money.addView(labeled("Формула остатка", spinner(new String[]{"Начислено − выплаты", "Чистыми − выплаты"}, new String[]{"gross_minus_payouts", "net_minus_payouts"}, settings.remainingFormulaMode, v -> {
            updateSetting("remainingFormulaMode", v);
            showSettings();
        })), matchWrapTop(dp(10)));
        money.addView(labeled("Округление", spinner(new String[]{"Копейки", "Целые", "Десятки"}, new String[]{"none", "integer", "tens"}, settings.roundingMode, v -> {
            updateSetting("roundingMode", v);
            showSettings();
        })), matchWrapTop(dp(10)));
        body.addView(money, matchWrapTop(dp(14)));

        LinearLayout goal = card();
        goal.addView(text("Цель дохода", 18, C_INK, true));
        EditText target = input(clean(activeGoalTarget()), "80000", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        goal.addView(labeled("Сумма цели на месяц", target), matchWrapTop(dp(10)));
        goal.addView(button("Сохранить цель", C_PRIMARY, Color.WHITE, () -> {
            saveGoal(number(target.getText().toString()));
            showSettings();
        }), matchWrapTop(dp(12)));
        body.addView(goal, matchWrapTop(dp(14)));

        LinearLayout files = card();
        files.addView(text("Файлы и данные", 18, C_INK, true));
        files.addView(button("Экспорт CSV", C_PRIMARY, Color.WHITE, () -> createFile("clean-income.csv", "text/csv", REQ_EXPORT_CSV)), matchWrapTop(dp(10)));
        files.addView(button("Резервная копия JSON", C_BORDER, C_INK, () -> createFile("clean-income-backup.json", "application/json", REQ_EXPORT_JSON)), matchWrapTop(dp(8)));
        files.addView(button("Импорт из JSON", C_BORDER, C_INK, () -> openFile()), matchWrapTop(dp(8)));
        files.addView(button("Очистить записи", C_NEGATIVE_SOFT, C_NEGATIVE, () -> confirm("Очистить записи?", "Работы и настройки останутся.", () -> {
            helper.clearFinancialData();
            showSettings();
        })), matchWrapTop(dp(8)));
        files.addView(button("Сбросить онбординг", C_BORDER, C_INK, () -> {
            updateSetting("hasCompletedOnboarding", 0);
            settings.hasCompletedOnboarding = false;
            showOnboarding();
        }), matchWrapTop(dp(8)));
        body.addView(files, matchWrapTop(dp(14)));
        setScreen("Настройки", "Карточки, экспорт, импорт и формулы", body, "more");
    }

    private void saveRecord(String kind, String editId, String jobId, String date, String start, String end, String breakMinutes,
                            boolean breakPaid, String paymentType, String payoutType, String category, String hourly,
                            String fixed, String amount, String tips, String bonus, String penalty, String note) {
        String time = IncomeDb.now();
        ContentValues values = new ContentValues();
        if (kind.equals("shift") || kind.equals("income")) {
            String id = editId == null ? IncomeDb.id("shift") : editId;
            values.put("id", id);
            values.put("jobId", jobId);
            values.put("date", safeDate(date));
            values.put("startDateTime", safeDate(date) + " " + cleanTime(start, "09:00"));
            values.put("endDateTime", safeDate(date) + " " + cleanTime(end, "18:00"));
            values.put("breakMinutes", (int) number(breakMinutes));
            values.put("isBreakPaid", breakPaid ? 1 : 0);
            String type = kind.equals("income") ? "manual" : paymentType;
            values.put("paymentType", type);
            values.put("hourlyRate", type.equals("hourly") ? number(hourly) : 0);
            values.put("fixedAmount", type.equals("fixed") ? number(fixed) : 0);
            values.put("grossAmountManual", (type.equals("manual") || type.equals("orders")) ? number(amount) : 0);
            values.put("tips", number(tips));
            values.put("bonus", number(bonus));
            values.put("penalty", number(penalty));
            values.put("note", note == null ? "" : note);
            values.put("createdAt", time);
            values.put("updatedAt", time);
            db.insertWithOnConflict("shifts", null, values, SQLiteDatabase.CONFLICT_REPLACE);
        } else if (kind.equals("expense")) {
            values.put("id", editId == null ? IncomeDb.id("expense") : editId);
            values.put("jobId", "none".equals(jobId) ? null : jobId);
            values.putNull("shiftId");
            values.put("date", safeDate(date));
            values.put("amount", number(amount));
            values.put("category", category);
            values.put("note", note == null ? "" : note);
            values.put("createdAt", time);
            values.put("updatedAt", time);
            db.insertWithOnConflict("expenses", null, values, SQLiteDatabase.CONFLICT_REPLACE);
        } else if (kind.equals("payout")) {
            values.put("id", editId == null ? IncomeDb.id("payout") : editId);
            values.put("jobId", "none".equals(jobId) ? null : jobId);
            values.put("date", safeDate(date));
            values.put("amount", number(amount));
            values.put("type", payoutType);
            values.put("note", note == null ? "" : note);
            values.put("createdAt", time);
            values.put("updatedAt", time);
            db.insertWithOnConflict("payouts", null, values, SQLiteDatabase.CONFLICT_REPLACE);
        }
    }

    private void duplicate(ActivityItem item) {
        String sourceTable = item.kind.equals("expense") ? "expenses" : item.kind.equals("payout") ? "payouts" : "shifts";
        Cursor c = db.rawQuery("SELECT * FROM " + sourceTable + " WHERE id=?", new String[]{item.id});
        if (c.moveToFirst()) {
            ContentValues values = new ContentValues();
            String[] names = c.getColumnNames();
            for (String name : names) putFromCursor(values, c, name);
            values.put("id", IncomeDb.id(item.kind));
            values.put("createdAt", IncomeDb.now());
            values.put("updatedAt", IncomeDb.now());
            db.insert(sourceTable, null, values);
            toast("Запись дублирована");
        }
        c.close();
        showHistory();
    }

    private void delete(ActivityItem item) {
        String table = item.kind.equals("expense") ? "expenses" : item.kind.equals("payout") ? "payouts" : "shifts";
        db.delete(table, "id=?", new String[]{item.id});
        toast("Удалено");
        showHistory();
    }

    private Summary summary(Range range) {
        Summary s = new Summary();
        Cursor shifts = db.rawQuery("SELECT * FROM shifts", null);
        while (shifts.moveToNext()) {
            if (!inRange(str(shifts, "date"), range)) continue;
            ShiftCalc calc = calcShift(shifts);
            s.gross += calc.gross;
            s.hours += calc.hours;
            s.shiftCount++;
        }
        shifts.close();
        Cursor expenses = db.rawQuery("SELECT * FROM expenses", null);
        while (expenses.moveToNext()) if (inRange(str(expenses, "date"), range)) s.expenses += num(expenses, "amount");
        expenses.close();
        Cursor payouts = db.rawQuery("SELECT * FROM payouts", null);
        while (payouts.moveToNext()) if (inRange(str(payouts, "date"), range)) s.paid += num(payouts, "amount");
        payouts.close();
        s.net = s.gross - s.expenses;
        s.remaining = (settings.remainingFormulaMode.equals("net_minus_payouts") ? s.net : s.gross) - s.paid;
        s.netPerHour = s.hours > 0 ? s.net / s.hours : 0;
        s.averageShiftNet = s.shiftCount > 0 ? s.net / s.shiftCount : 0;
        s.goalTarget = activeGoalTarget();
        s.goalProgress = s.goalTarget > 0 ? Math.max(0, Math.min(1, s.net / s.goalTarget)) : 0;
        return s;
    }

    private ShiftCalc calcShift(Cursor c) {
        ShiftCalc calc = new ShiftCalc();
        LocalDateTime start = parseDateTime(str(c, "startDateTime"));
        LocalDateTime end = parseDateTime(str(c, "endDateTime"));
        if (!end.isAfter(start)) end = end.plusDays(1);
        double totalHours = Math.max(0, java.time.Duration.between(start, end).toMinutes() / 60.0);
        String type = str(c, "paymentType");
        boolean standaloneIncome = (type.equals("manual") || type.equals("orders")) && totalHours <= 1.0 / 60.0;
        double unpaidBreak = intval(c, "isBreakPaid") == 1 ? 0 : intval(c, "breakMinutes") / 60.0;
        calc.hours = standaloneIncome ? 0 : Math.max(0, totalHours - unpaidBreak);
        if (type.equals("hourly")) calc.base = calc.hours * num(c, "hourlyRate");
        else if (type.equals("fixed")) calc.base = num(c, "fixedAmount");
        else calc.base = num(c, "grossAmountManual");
        calc.gross = calc.base + num(c, "tips") + num(c, "bonus") - num(c, "penalty");
        return calc;
    }

    private ArrayList<ActivityItem> activities(Range range) {
        ArrayList<ActivityItem> items = new ArrayList<>();
        Cursor shifts = db.rawQuery("SELECT * FROM shifts", null);
        while (shifts.moveToNext()) {
            if (!inRange(str(shifts, "date"), range)) continue;
            String paymentType = str(shifts, "paymentType");
            String kind = paymentType.equals("manual") || paymentType.equals("orders") ? "income" : "shift";
            ShiftCalc calc = calcShift(shifts);
            items.add(new ActivityItem(str(shifts, "id"), kind, str(shifts, "date"), kind.equals("income") ? "Доход" : "Смена", jobName(str(shifts, "jobId")), calc.gross, jobColor(str(shifts, "jobId"))));
        }
        shifts.close();
        Cursor expenses = db.rawQuery("SELECT * FROM expenses", null);
        while (expenses.moveToNext()) {
            if (!inRange(str(expenses, "date"), range)) continue;
            items.add(new ActivityItem(str(expenses, "id"), "expense", str(expenses, "date"), expenseLabel(str(expenses, "category")), jobName(str(expenses, "jobId")), -num(expenses, "amount"), "#E25555"));
        }
        expenses.close();
        Cursor payouts = db.rawQuery("SELECT * FROM payouts", null);
        while (payouts.moveToNext()) {
            if (!inRange(str(payouts, "date"), range)) continue;
            items.add(new ActivityItem(str(payouts, "id"), "payout", str(payouts, "date"), payoutLabel(str(payouts, "type")), jobName(str(payouts, "jobId")), num(payouts, "amount"), "#0F766E"));
        }
        payouts.close();
        Collections.sort(items, (a, b) -> b.date.compareTo(a.date));
        return items;
    }

    private ArrayList<DayValue> valuesByDay(Range range) {
        HashMap<String, DayValue> map = new HashMap<>();
        Cursor shifts = db.rawQuery("SELECT * FROM shifts", null);
        while (shifts.moveToNext()) {
            String date = str(shifts, "date");
            if (!inRange(date, range)) continue;
            DayValue value = map.containsKey(date) ? map.get(date) : new DayValue(date);
            value.gross += calcShift(shifts).gross;
            value.net = value.gross - value.expenses;
            map.put(date, value);
        }
        shifts.close();
        Cursor expenses = db.rawQuery("SELECT * FROM expenses", null);
        while (expenses.moveToNext()) {
            String date = str(expenses, "date");
            if (!inRange(date, range)) continue;
            DayValue value = map.containsKey(date) ? map.get(date) : new DayValue(date);
            value.expenses += num(expenses, "amount");
            value.net = value.gross - value.expenses;
            map.put(date, value);
        }
        expenses.close();
        ArrayList<DayValue> result = new ArrayList<>(map.values());
        Collections.sort(result, (a, b) -> a.date.compareTo(b.date));
        if (result.size() > 14) return new ArrayList<>(result.subList(result.size() - 14, result.size()));
        return result;
    }

    private ArrayList<JobValue> valuesByJob(Range range) {
        ArrayList<JobValue> result = new ArrayList<>();
        for (Job job : jobs(true)) {
            double gross = 0;
            double expenses = 0;
            Cursor shifts = db.rawQuery("SELECT * FROM shifts WHERE jobId=?", new String[]{job.id});
            while (shifts.moveToNext()) if (inRange(str(shifts, "date"), range)) gross += calcShift(shifts).gross;
            shifts.close();
            Cursor exp = db.rawQuery("SELECT * FROM expenses WHERE jobId=?", new String[]{job.id});
            while (exp.moveToNext()) if (inRange(str(exp, "date"), range)) expenses += num(exp, "amount");
            exp.close();
            if (gross != 0 || expenses != 0) result.add(new JobValue(job.name, job.color, gross - expenses));
        }
        Collections.sort(result, (a, b) -> Double.compare(b.net, a.net));
        return result;
    }

    private HashMap<String, Double> expensesByCategory(Range range) {
        HashMap<String, Double> result = new HashMap<>();
        Cursor c = db.rawQuery("SELECT * FROM expenses", null);
        while (c.moveToNext()) {
            if (!inRange(str(c, "date"), range)) continue;
            String category = str(c, "category");
            result.put(category, (result.containsKey(category) ? result.get(category) : 0) + num(c, "amount"));
        }
        c.close();
        return result;
    }

    private void loadSettings() {
        Cursor c = db.rawQuery("SELECT * FROM settings WHERE id='settings'", null);
        if (c.moveToFirst()) {
            settings = new Settings();
            settings.currency = str(c, "currency");
            settings.themeMode = str(c, "themeMode");
            settings.weekStartDay = intval(c, "weekStartDay");
            settings.defaultHomePeriod = str(c, "defaultHomePeriod");
            settings.showGrossCard = bool(c, "showGrossCard");
            settings.showExpensesCard = bool(c, "showExpensesCard");
            settings.showNetCard = bool(c, "showNetCard");
            settings.showRemainingCard = bool(c, "showRemainingCard");
            settings.showNetPerHourCard = bool(c, "showNetPerHourCard");
            settings.showGoalCard = bool(c, "showGoalCard");
            settings.showRecentActivity = bool(c, "showRecentActivity");
            settings.homeCardOrder = str(c, "homeCardOrder");
            settings.remainingFormulaMode = str(c, "remainingFormulaMode");
            settings.roundingMode = str(c, "roundingMode");
            settings.notificationsEnabled = bool(c, "notificationsEnabled");
            settings.hasCompletedOnboarding = bool(c, "hasCompletedOnboarding");
            settings.homeCompactMode = bool(c, "homeCompactMode");
        }
        c.close();
    }

    private ArrayList<Job> jobs(boolean includeArchived) {
        ArrayList<Job> result = new ArrayList<>();
        Cursor c = db.rawQuery(includeArchived ? "SELECT * FROM jobs ORDER BY isArchived, createdAt DESC" : "SELECT * FROM jobs WHERE isArchived=0 ORDER BY createdAt DESC", null);
        while (c.moveToNext()) {
            Job job = new Job();
            job.id = str(c, "id");
            job.name = str(c, "name");
            job.type = str(c, "type");
            job.hourlyRate = num(c, "defaultHourlyRate");
            job.fixed = num(c, "defaultFixedAmount");
            job.color = str(c, "color");
            job.archived = intval(c, "isArchived");
            result.add(job);
        }
        c.close();
        return result;
    }

    private void saveJob(String editId, String name, String type, String rate, String fixed, String color, int archived) {
        if (name == null || name.trim().isEmpty()) {
            toast("Введите название работы");
            return;
        }
        ContentValues v = new ContentValues();
        String time = IncomeDb.now();
        v.put("id", editId == null ? IncomeDb.id("job") : editId);
        v.put("name", name.trim());
        v.put("type", type);
        v.put("defaultHourlyRate", number(rate));
        v.put("defaultFixedAmount", number(fixed));
        v.put("color", color);
        v.put("currency", settings.currency);
        v.put("isArchived", archived);
        v.put("createdAt", time);
        v.put("updatedAt", time);
        db.insertWithOnConflict("jobs", null, v, SQLiteDatabase.CONFLICT_REPLACE);
        toast("Работа сохранена");
    }

    private void archiveJob(String id, int archived) {
        ContentValues values = new ContentValues();
        values.put("isArchived", archived);
        values.put("updatedAt", IncomeDb.now());
        db.update("jobs", values, "id=?", new String[]{id});
    }

    private void saveGoal(double amount) {
        String now = IncomeDb.now();
        LocalDate today = LocalDate.now();
        db.delete("goals", null, null);
        ContentValues v = new ContentValues();
        v.put("id", IncomeDb.id("goal"));
        v.put("periodType", "month");
        v.put("targetAmount", amount);
        v.put("startDate", today.withDayOfMonth(1).format(DATE));
        v.put("endDate", today.withDayOfMonth(today.lengthOfMonth()).format(DATE));
        v.put("isActive", 1);
        v.put("createdAt", now);
        v.put("updatedAt", now);
        db.insert("goals", null, v);
        toast("Цель сохранена");
    }

    private double activeGoalTarget() {
        Cursor c = db.rawQuery("SELECT targetAmount FROM goals WHERE isActive=1 ORDER BY createdAt DESC LIMIT 1", null);
        double value = c.moveToFirst() ? c.getDouble(0) : 0;
        c.close();
        return value;
    }

    private void updateSetting(String key, Object value) {
        ContentValues values = new ContentValues();
        if (value instanceof Integer) values.put(key, (Integer) value);
        else if (value instanceof Boolean) values.put(key, (Boolean) value ? 1 : 0);
        else values.put(key, String.valueOf(value));
        db.update("settings", values, "id='settings'", null);
        loadSettings();
    }

    private void toggleSetting(String key, boolean current) {
        updateSetting(key, current ? 0 : 1);
        showSettings();
    }

    private void moveCard(String card, int direction) {
        String[] cards = settings.homeCardOrder.split(",");
        int index = -1;
        for (int i = 0; i < cards.length; i++) if (cards[i].equals(card)) index = i;
        int next = index + direction;
        if (index < 0 || next < 0 || next >= cards.length) return;
        String tmp = cards[index];
        cards[index] = cards[next];
        cards[next] = tmp;
        updateSetting("homeCardOrder", join(cards));
        showSettings();
    }

    private Prefill prefill(String kind, String editId, ArrayList<Job> jobs) {
        Prefill p = new Prefill();
        p.kind = kind;
        p.date = LocalDate.now().format(DATE);
        p.startTime = "09:00";
        p.endTime = "18:00";
        p.breakMinutes = "0";
        p.paymentType = "hourly";
        p.payoutType = "payout";
        p.category = "fuel";
        p.jobId = jobs.isEmpty() ? "" : jobs.get(0).id;
        p.hourlyRate = jobs.isEmpty() ? "" : clean(jobs.get(0).hourlyRate);
        p.fixedAmount = "";
        p.amount = "";
        p.tips = "";
        p.bonus = "";
        p.penalty = "";
        p.note = "";
        if (editId == null) return p;

        Cursor s = db.rawQuery("SELECT * FROM shifts WHERE id=?", new String[]{editId});
        if (s.moveToFirst()) {
            String payment = str(s, "paymentType");
            p.kind = payment.equals("manual") || payment.equals("orders") ? "income" : "shift";
            p.date = str(s, "date");
            p.jobId = str(s, "jobId");
            p.startTime = timePart(str(s, "startDateTime"));
            p.endTime = timePart(str(s, "endDateTime"));
            p.breakMinutes = String.valueOf(intval(s, "breakMinutes"));
            p.isBreakPaid = intval(s, "isBreakPaid") == 1;
            p.paymentType = payment.equals("manual") ? "hourly" : payment;
            p.hourlyRate = clean(num(s, "hourlyRate"));
            p.fixedAmount = clean(num(s, "fixedAmount"));
            p.amount = clean(num(s, "grossAmountManual"));
            p.tips = clean(num(s, "tips"));
            p.bonus = clean(num(s, "bonus"));
            p.penalty = clean(num(s, "penalty"));
            p.note = str(s, "note");
            s.close();
            return p;
        }
        s.close();
        Cursor e = db.rawQuery("SELECT * FROM expenses WHERE id=?", new String[]{editId});
        if (e.moveToFirst()) {
            p.kind = "expense";
            p.date = str(e, "date");
            p.jobId = str(e, "jobId").isEmpty() ? "none" : str(e, "jobId");
            p.amount = clean(num(e, "amount"));
            p.category = str(e, "category");
            p.note = str(e, "note");
            e.close();
            return p;
        }
        e.close();
        Cursor pay = db.rawQuery("SELECT * FROM payouts WHERE id=?", new String[]{editId});
        if (pay.moveToFirst()) {
            p.kind = "payout";
            p.date = str(pay, "date");
            p.jobId = str(pay, "jobId").isEmpty() ? "none" : str(pay, "jobId");
            p.amount = clean(num(pay, "amount"));
            p.payoutType = str(pay, "type");
            p.note = str(pay, "note");
        }
        pay.close();
        return p;
    }

    private void setScreen(String title, String subtitle, View body, String selected) {
        Log.d(TAG, "setScreen: " + selected + " / " + title);
        LinearLayout root = vertical();
        root.setBackgroundColor(C_BG);
        ScrollView scroll = new ScrollView(this);
        LinearLayout content = vertical();
        content.setPadding(dp(16), dp(24), dp(16), dp(24));
        TextView titleView = text(title, 30, C_INK, true);
        content.addView(titleView);
        if (subtitle != null && !subtitle.isEmpty()) content.addView(text(subtitle, 14, C_MUTED, false), matchWrapTop(dp(4)));
        content.addView(body, matchWrapTop(dp(18)));
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        root.addView(bottomNav(selected), matchWrap());
        setContentView(root);
    }

    private LinearLayout bottomNav(String selected) {
        LinearLayout nav = horizontal();
        nav.setPadding(dp(6), dp(8), dp(6), dp(10));
        nav.setBackgroundColor(C_SURFACE);
        nav.addView(tab("Главная", "home", selected, () -> showHome()), weightWrap(1));
        nav.addView(tab("История", "history", selected, () -> showHistory()), weightWrap(1));
        nav.addView(tab("Добавить", "add", selected, () -> showAdd("shift", null)), weightWrap(1));
        nav.addView(tab("Аналитика", "analytics", selected, () -> showAnalytics()), weightWrap(1));
        nav.addView(tab("Еще", "more", selected, () -> showMore()), weightWrap(1));
        return nav;
    }

    private TextView tab(String label, String id, String selected, ClickAction action) {
        TextView v = text(label, 11, id.equals(selected) ? C_PRIMARY_DARK : C_MUTED, true);
        v.setGravity(Gravity.CENTER);
        v.setPadding(dp(2), dp(10), dp(2), dp(10));
        v.setOnClickListener(view -> navigate(action));
        return v;
    }

    private LinearLayout periodButtons(String current, ValueCallback callback) {
        LinearLayout row = horizontal();
        addPeriod(row, "День", "today", current, callback);
        addPeriod(row, "Неделя", "week", current, callback);
        addPeriod(row, "Месяц", "month", current, callback);
        addPeriod(row, "Все", "all", current, callback);
        return row;
    }

    private void addPeriod(LinearLayout row, String label, String value, String current, ValueCallback callback) {
        TextView v = button(label, value.equals(current) ? C_SURFACE : C_BORDER, C_INK, () -> callback.call(value));
        row.addView(v, weightWrapLeft(1, row.getChildCount() == 0 ? 0 : dp(6)));
    }

    private LinearLayout metric(String title, String value, String note, int softColor) {
        LinearLayout card = card();
        card.addView(text(title, 13, C_MUTED, true));
        card.addView(text(value, 27, C_INK, true), matchWrapTop(dp(4)));
        if (note != null && !note.isEmpty()) card.addView(text(note, 13, C_MUTED, false), matchWrapTop(dp(2)));
        return card;
    }

    private LinearLayout twoMetrics(String t1, String v1, String t2, String v2) {
        LinearLayout row = horizontal();
        row.addView(metric(t1, v1, "", C_PRIMARY_SOFT), weightWrap(1));
        row.addView(metric(t2, v2, "", C_PRIMARY_SOFT), weightWrapLeft(1, dp(10)));
        return row;
    }

    private LinearLayout goalCard(Summary s) {
        LinearLayout card = card();
        card.addView(text("Цель дохода", 13, C_MUTED, true));
        card.addView(text(money(s.goalTarget, true), 22, C_INK, true));
        LinearLayout track = new LinearLayout(this);
        track.setBackground(round(C_BORDER, dp(999), C_BORDER, 0));
        LinearLayout fill = new LinearLayout(this);
        fill.setBackground(round(C_PRIMARY, dp(999), C_PRIMARY, 0));
        track.addView(fill, new LinearLayout.LayoutParams(0, dp(9), (float) Math.max(0.03, s.goalProgress)));
        track.addView(new View(this), new LinearLayout.LayoutParams(0, dp(9), (float) Math.max(0.01, 1 - s.goalProgress)));
        card.addView(track, matchWrapTop(dp(12)));
        card.addView(text(Math.round(s.goalProgress * 100) + "% выполнено", 13, C_MUTED, false), matchWrapTop(dp(6)));
        return card;
    }

    private View activityRow(ActivityItem item, boolean controls) {
        LinearLayout wrap = vertical();
        LinearLayout row = horizontal();
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView marker = new TextView(this);
        marker.setBackground(round(parseColor(item.color, C_PRIMARY), dp(6), parseColor(item.color, C_PRIMARY), 0));
        row.addView(marker, box(dp(12), dp(12)));
        LinearLayout texts = vertical();
        texts.addView(text(item.title, 15, C_INK, true));
        texts.addView(text(shortDate(item.date) + " · " + item.subtitle, 13, C_MUTED, false));
        row.addView(texts, weightWrapLeft(1, dp(10)));
        row.addView(text(money(item.amount, true), 14, item.amount < 0 ? C_NEGATIVE : C_INK, true));
        wrap.addView(row, matchWrapTop(dp(10)));
        if (controls) {
            LinearLayout buttons = horizontal();
            buttons.addView(button("Ред.", C_BORDER, C_INK, () -> showAdd(item.kind, item.id)), weightWrap(1));
            buttons.addView(button("Дубль", C_BORDER, C_INK, () -> duplicate(item)), weightWrapLeft(1, dp(6)));
            buttons.addView(button("Удалить", C_NEGATIVE_SOFT, C_NEGATIVE, () -> confirm("Удалить запись?", "Это действие нельзя отменить.", () -> delete(item))), weightWrapLeft(1, dp(6)));
            wrap.addView(buttons, matchWrapTop(dp(8)));
        }
        return wrap;
    }

    private View simpleRow(String title, String subtitle, String amount, int markerColor) {
        LinearLayout row = horizontal();
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView marker = new TextView(this);
        marker.setBackground(round(markerColor, dp(6), markerColor, 0));
        row.addView(marker, box(dp(12), dp(12)));
        LinearLayout texts = vertical();
        texts.addView(text(title, 15, C_INK, true));
        texts.addView(text(subtitle, 13, C_MUTED, false));
        row.addView(texts, weightWrapLeft(1, dp(10)));
        row.addView(text(amount, 15, C_INK, true));
        return row;
    }

    private View navRow(String title, String subtitle, ClickAction action) {
        LinearLayout row = horizontal();
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout texts = vertical();
        texts.addView(text(title, 16, C_INK, true));
        texts.addView(text(subtitle, 13, C_MUTED, false));
        row.addView(texts, weightWrap(1));
        row.addView(text("›", 28, C_MUTED, true));
        row.setPadding(0, dp(12), 0, dp(12));
        row.setOnClickListener(v -> navigate(action));
        return row;
    }

    private View bar(String label, double value, double max, int color) {
        LinearLayout block = vertical();
        LinearLayout top = horizontal();
        top.addView(text(label, 13, C_TEXT, true), weightWrap(1));
        top.addView(text(money(value, true), 13, C_MUTED, true));
        block.addView(top, matchWrapTop(dp(10)));
        LinearLayout track = new LinearLayout(this);
        track.setBackground(round(C_BORDER, dp(999), C_BORDER, 0));
        LinearLayout fill = new LinearLayout(this);
        fill.setBackground(round(color, dp(999), color, 0));
        float weight = (float) Math.max(0.04, Math.min(1, Math.abs(value) / max));
        track.addView(fill, new LinearLayout.LayoutParams(0, dp(9), weight));
        track.addView(new View(this), new LinearLayout.LayoutParams(0, dp(9), 1 - weight));
        block.addView(track, matchWrapTop(dp(6)));
        return block;
    }

    private View toggleRow(String label, String key, boolean value) {
        LinearLayout row = horizontal();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(text(label, 15, C_INK, true), weightWrap(1));
        row.addView(button(value ? "Вкл" : "Выкл", value ? C_PRIMARY_SOFT : C_BORDER, value ? C_PRIMARY_DARK : C_MUTED, () -> toggleSetting(key, value)), wrap());
        return row;
    }

    private View settingPeriod(String label, String value) {
        return labeled(label, spinner(new String[]{"День", "Неделя", "Месяц", "Все"}, new String[]{"today", "week", "month", "all"}, value, v -> {
            updateSetting("defaultHomePeriod", v);
            showSettings();
        }));
    }

    private LinearLayout twoButtons(String a, ClickAction aa, String b, ClickAction bb) {
        LinearLayout row = horizontal();
        row.addView(button(a, C_SURFACE, C_INK, aa), weightWrap(1));
        row.addView(button(b, C_SURFACE, C_INK, bb), weightWrapLeft(1, dp(10)));
        return row;
    }

    private LinearLayout twoFields(String a, EditText aa, String b, EditText bb) {
        LinearLayout row = horizontal();
        row.addView(labeled(a, aa), weightWrap(1));
        row.addView(labeled(b, bb), weightWrapLeft(1, dp(10)));
        return row;
    }

    private LinearLayout labeled(String label, View input) {
        LinearLayout box = vertical();
        box.addView(text(label, 13, C_TEXT, true));
        box.addView(input, matchWrapTop(dp(5)));
        return box;
    }

    private TextView button(String label, int background, int foreground, ClickAction action) {
        TextView v = text(label, 14, foreground, true);
        v.setGravity(Gravity.CENTER);
        v.setPadding(dp(12), dp(11), dp(12), dp(11));
        v.setBackground(round(background, dp(15), background == C_SURFACE ? C_BORDER : background, background == C_PRIMARY ? 0 : dp(1)));
        v.setOnClickListener(view -> navigate(action));
        return v;
    }

    private EditText input(String value, String hint, int inputType) {
        EditText e = new EditText(this);
        e.setText(value == null ? "" : value);
        e.setHint(hint);
        e.setTextColor(C_INK);
        e.setHintTextColor(C_MUTED);
        e.setTextSize(16);
        e.setSingleLine((inputType & InputType.TYPE_TEXT_FLAG_MULTI_LINE) == 0);
        e.setInputType(inputType);
        e.setPadding(dp(12), dp(4), dp(12), dp(4));
        e.setBackground(round(C_SURFACE, dp(14), C_BORDER, dp(1)));
        return e;
    }

    private CheckBox check(String label, boolean checked) {
        CheckBox box = new CheckBox(this);
        box.setText(label);
        box.setTextColor(C_TEXT);
        box.setTextSize(14);
        box.setChecked(checked);
        return box;
    }

    private Spinner spinner(String[] labels, String[] values, String selected, ValueCallback callback) {
        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, labels);
        spinner.setAdapter(adapter);
        int selectedIndex = 0;
        for (int i = 0; i < values.length; i++) if (values[i].equals(selected)) selectedIndex = i;
        spinner.setSelection(selectedIndex);
        spinner.setBackground(round(C_SURFACE, dp(14), C_BORDER, dp(1)));
        spinner.setPadding(dp(8), 0, dp(8), 0);
        final boolean[] armed = {false};
        spinner.post(() -> armed[0] = true);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (!armed[0]) return;
                if (values[position].equals(selected)) return;
                callback.call(values[position]);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        return spinner;
    }

    private LinearLayout card() {
        LinearLayout v = vertical();
        v.setPadding(dp(16), dp(16), dp(16), dp(16));
        v.setBackground(round(C_SURFACE, dp(20), C_BORDER, dp(1)));
        return v;
    }

    private View empty(String title, String subtitle) {
        LinearLayout e = vertical();
        e.setPadding(dp(14), dp(18), dp(14), dp(18));
        e.setGravity(Gravity.CENTER);
        e.setBackground(round(C_BG, dp(18), C_BORDER, dp(1)));
        TextView t = text(title, 15, C_INK, true);
        t.setGravity(Gravity.CENTER);
        e.addView(t);
        if (subtitle != null && !subtitle.isEmpty()) {
            TextView s = text(subtitle, 13, C_MUTED, false);
            s.setGravity(Gravity.CENTER);
            e.addView(s, matchWrapTop(dp(4)));
        }
        return e;
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value == null ? "" : value);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setIncludeFontPadding(true);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private LinearLayout vertical() {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        return v;
    }

    private LinearLayout horizontal() {
        LinearLayout h = new LinearLayout(this);
        h.setOrientation(LinearLayout.HORIZONTAL);
        return h;
    }

    private GradientDrawable round(int color, int radius, int strokeColor, int strokeWidth) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        if (strokeWidth > 0) d.setStroke(strokeWidth, strokeColor);
        return d;
    }

    private LinearLayout.LayoutParams matchWrap() { return new LinearLayout.LayoutParams(-1, -2); }
    private LinearLayout.LayoutParams matchWrapTop(int top) { LinearLayout.LayoutParams p = matchWrap(); p.topMargin = top; return p; }
    private LinearLayout.LayoutParams wrap() { return new LinearLayout.LayoutParams(-2, -2); }
    private LinearLayout.LayoutParams wrapLeft(int left) { LinearLayout.LayoutParams p = wrap(); p.leftMargin = left; return p; }
    private LinearLayout.LayoutParams weightWrap(float w) { return new LinearLayout.LayoutParams(0, -2, w); }
    private LinearLayout.LayoutParams weightWrapLeft(float w, int left) { LinearLayout.LayoutParams p = weightWrap(w); p.leftMargin = left; return p; }
    private LinearLayout.LayoutParams box(int width, int height) { return new LinearLayout.LayoutParams(width, height); }
    private LinearLayout.LayoutParams boxLeft(int width, int height, int left) { LinearLayout.LayoutParams p = box(width, height); p.leftMargin = left; return p; }
    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + 0.5f); }

    private Range range(String period) {
        LocalDate today = LocalDate.now();
        Range r = new Range();
        r.all = false;
        if (period.equals("today")) {
            r.label = "Сегодня";
            r.start = today;
            r.end = today;
        } else if (period.equals("week")) {
            int offset = today.getDayOfWeek().getValue() - (settings.weekStartDay == 1 ? DayOfWeek.MONDAY.getValue() : DayOfWeek.SUNDAY.getValue());
            if (offset < 0) offset += 7;
            r.start = today.minusDays(offset);
            r.end = r.start.plusDays(6);
            r.label = "Эта неделя";
        } else if (period.equals("month")) {
            r.start = today.withDayOfMonth(1);
            r.end = today.withDayOfMonth(today.lengthOfMonth());
            r.label = "Этот месяц";
        } else {
            r.all = true;
            r.label = "Все время";
        }
        return r;
    }

    private boolean inRange(String date, Range range) {
        if (range.all) return true;
        try {
            LocalDate d = LocalDate.parse(date, DATE);
            return !d.isBefore(range.start) && !d.isAfter(range.end);
        } catch (Exception e) {
            return false;
        }
    }

    private String safeDate(String value) {
        try {
            return LocalDate.parse(value.trim(), DATE).format(DATE);
        } catch (Exception e) {
            return LocalDate.now().format(DATE);
        }
    }

    private String cleanTime(String value, String fallback) {
        try {
            return LocalTime.parse(value.trim()).toString();
        } catch (Exception e) {
            return fallback;
        }
    }

    private LocalDateTime parseDateTime(String value) {
        try {
            return LocalDateTime.parse(value, DATE_TIME);
        } catch (Exception ignored) {
            try {
                return LocalDateTime.parse(value);
            } catch (Exception e) {
                return LocalDateTime.now();
            }
        }
    }

    private String timePart(String value) {
        LocalDateTime time = parseDateTime(value);
        return String.format(Locale.US, "%02d:%02d", time.getHour(), time.getMinute());
    }

    private String money(double value, boolean compact) {
        double v = value;
        if (settings.roundingMode.equals("integer")) v = Math.round(v);
        if (settings.roundingMode.equals("tens")) v = Math.round(v / 10.0) * 10;
        NumberFormat nf = NumberFormat.getCurrencyInstance(new Locale("ru", "RU"));
        try {
            nf.setCurrency(Currency.getInstance(settings.currency));
        } catch (Exception ignored) {
        }
        nf.setMaximumFractionDigits(compact || !settings.roundingMode.equals("none") ? 0 : 2);
        return nf.format(v);
    }

    private String one(double value) {
        return String.format(Locale.US, "%.1f", value);
    }

    private double number(String value) {
        if (value == null) return 0;
        try {
            return Double.parseDouble(value.trim().replace(",", "."));
        } catch (Exception e) {
            return 0;
        }
    }

    private String clean(double value) {
        if (Math.abs(value - Math.round(value)) < 0.001) return String.valueOf((long) Math.round(value));
        return String.valueOf(value);
    }

    private String str(Cursor c, String col) {
        int i = c.getColumnIndex(col);
        if (i < 0 || c.isNull(i)) return "";
        return c.getString(i);
    }

    private double num(Cursor c, String col) {
        int i = c.getColumnIndex(col);
        if (i < 0 || c.isNull(i)) return 0;
        return c.getDouble(i);
    }

    private int intval(Cursor c, String col) {
        int i = c.getColumnIndex(col);
        if (i < 0 || c.isNull(i)) return 0;
        return c.getInt(i);
    }

    private boolean bool(Cursor c, String col) {
        return intval(c, col) == 1;
    }

    private String jobName(String id) {
        if (id == null || id.isEmpty()) return "Общее";
        Cursor c = db.rawQuery("SELECT name FROM jobs WHERE id=?", new String[]{id});
        String name = c.moveToFirst() ? c.getString(0) : "Без работы";
        c.close();
        return name;
    }

    private String jobColor(String id) {
        if (id == null || id.isEmpty()) return "#14B87A";
        Cursor c = db.rawQuery("SELECT color FROM jobs WHERE id=?", new String[]{id});
        String color = c.moveToFirst() ? c.getString(0) : "#14B87A";
        c.close();
        return color;
    }

    private int parseColor(String color, int fallback) {
        try {
            return Color.parseColor(color);
        } catch (Exception e) {
            return fallback;
        }
    }

    private String shortDate(String date) {
        try {
            LocalDate d = LocalDate.parse(date, DATE);
            return String.format(Locale.US, "%02d.%02d", d.getDayOfMonth(), d.getMonthValue());
        } catch (Exception e) {
            return date;
        }
    }

    private String expenseLabel(String value) {
        Map<String, String> map = expenseLabels();
        return map.containsKey(value) ? map.get(value) : "Другое";
    }

    private Map<String, String> expenseLabels() {
        HashMap<String, String> map = new HashMap<>();
        map.put("fuel", "Бензин");
        map.put("transport_rent", "Аренда транспорта");
        map.put("repair", "Ремонт");
        map.put("parking", "Парковка");
        map.put("phone", "Связь");
        map.put("food", "Еда");
        map.put("commission", "Комиссия");
        map.put("tax", "Налог");
        map.put("supplies", "Расходники");
        map.put("other", "Другое");
        return map;
    }

    private String[] expenseValueArray() {
        return new String[]{"fuel", "transport_rent", "repair", "parking", "phone", "food", "commission", "tax", "supplies", "other"};
    }

    private String[] expenseLabelArray() {
        String[] values = expenseValueArray();
        String[] labels = new String[values.length];
        for (int i = 0; i < values.length; i++) labels[i] = expenseLabel(values[i]);
        return labels;
    }

    private String payoutLabel(String type) {
        if (type.equals("advance")) return "Аванс";
        if (type.equals("correction")) return "Коррекция";
        return "Выплата";
    }

    private String cardLabel(String card) {
        if (card.equals("net")) return "Чистый доход";
        if (card.equals("gross")) return "Начислено";
        if (card.equals("expenses")) return "Расходы";
        if (card.equals("remaining")) return "Осталось получить";
        if (card.equals("netPerHour")) return "Чистыми в час";
        if (card.equals("goal")) return "Цель";
        return card;
    }

    private String join(String[] values) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) b.append(",");
            b.append(values[i]);
        }
        return b.toString();
    }

    private void createFile(String name, String mime, int requestCode) {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(mime);
        intent.putExtra(Intent.EXTRA_TITLE, name);
        startActivityForResult(intent, requestCode);
    }

    private void openFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        startActivityForResult(intent, REQ_IMPORT_JSON);
    }

    private void writeUri(Uri uri, String text) throws Exception {
        OutputStream out = getContentResolver().openOutputStream(uri);
        if (out == null) throw new Exception("Нет доступа к файлу");
        out.write(text.getBytes(StandardCharsets.UTF_8));
        out.close();
    }

    private String readUri(Uri uri) throws Exception {
        InputStream in = getContentResolver().openInputStream(uri);
        if (in == null) throw new Exception("Нет доступа к файлу");
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] bytes = new byte[4096];
        int read;
        while ((read = in.read(bytes)) != -1) buffer.write(bytes, 0, read);
        in.close();
        return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
    }

    private String buildCsv() {
        StringBuilder b = new StringBuilder();
        b.append("type,date,job,category,hours,gross,expense,payout,note\n");
        Cursor shifts = db.rawQuery("SELECT * FROM shifts ORDER BY date DESC", null);
        while (shifts.moveToNext()) {
            ShiftCalc c = calcShift(shifts);
            String payment = str(shifts, "paymentType");
            b.append(csv(payment.equals("manual") || payment.equals("orders") ? "income" : "shift")).append(",");
            b.append(csv(str(shifts, "date"))).append(",");
            b.append(csv(jobName(str(shifts, "jobId")))).append(",");
            b.append(csv(payment)).append(",");
            b.append(csv(one(c.hours))).append(",");
            b.append(csv(String.valueOf(c.gross))).append(",,,");
            b.append(csv(str(shifts, "note"))).append("\n");
        }
        shifts.close();
        Cursor expenses = db.rawQuery("SELECT * FROM expenses ORDER BY date DESC", null);
        while (expenses.moveToNext()) {
            b.append("expense,").append(csv(str(expenses, "date"))).append(",");
            b.append(csv(jobName(str(expenses, "jobId")))).append(",");
            b.append(csv(expenseLabel(str(expenses, "category")))).append(",,,");
            b.append(csv(String.valueOf(num(expenses, "amount")))).append(",");
            b.append(",");
            b.append(csv(str(expenses, "note"))).append("\n");
        }
        expenses.close();
        Cursor payouts = db.rawQuery("SELECT * FROM payouts ORDER BY date DESC", null);
        while (payouts.moveToNext()) {
            b.append("payout,").append(csv(str(payouts, "date"))).append(",");
            b.append(csv(jobName(str(payouts, "jobId")))).append(",");
            b.append(csv(str(payouts, "type"))).append(",,,,");
            b.append(csv(String.valueOf(num(payouts, "amount")))).append(",");
            b.append(csv(str(payouts, "note"))).append("\n");
        }
        payouts.close();
        return b.toString();
    }

    private String csv(String value) {
        String v = value == null ? "" : value.replace("\"", "\"\"");
        return "\"" + v + "\"";
    }

    private String buildBackupJson() throws Exception {
        JSONObject root = new JSONObject();
        root.put("jobs", tableJson("jobs"));
        root.put("shifts", tableJson("shifts"));
        root.put("expenses", tableJson("expenses"));
        root.put("payouts", tableJson("payouts"));
        root.put("goals", tableJson("goals"));
        root.put("settings", tableJson("settings"));
        return root.toString(2);
    }

    private JSONArray tableJson(String table) throws Exception {
        JSONArray array = new JSONArray();
        Cursor c = db.rawQuery("SELECT * FROM " + table, null);
        while (c.moveToNext()) {
            JSONObject object = new JSONObject();
            for (String column : c.getColumnNames()) {
                int index = c.getColumnIndex(column);
                if (c.isNull(index)) object.put(column, JSONObject.NULL);
                else if (c.getType(index) == Cursor.FIELD_TYPE_INTEGER) object.put(column, c.getLong(index));
                else if (c.getType(index) == Cursor.FIELD_TYPE_FLOAT) object.put(column, c.getDouble(index));
                else object.put(column, c.getString(index));
            }
            array.put(object);
        }
        c.close();
        return array;
    }

    private void importBackupJson(String text) throws Exception {
        JSONObject root = new JSONObject(text);
        db.beginTransaction();
        try {
            db.delete("jobs", null, null);
            db.delete("shifts", null, null);
            db.delete("expenses", null, null);
            db.delete("payouts", null, null);
            db.delete("goals", null, null);
            db.delete("settings", null, null);
            insertJson("jobs", root.optJSONArray("jobs"));
            insertJson("shifts", root.optJSONArray("shifts"));
            insertJson("expenses", root.optJSONArray("expenses"));
            insertJson("payouts", root.optJSONArray("payouts"));
            insertJson("goals", root.optJSONArray("goals"));
            insertJson("settings", root.optJSONArray("settings"));
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        helper.ensureSeed();
    }

    private void insertJson(String table, JSONArray array) throws Exception {
        if (array == null) return;
        for (int i = 0; i < array.length(); i++) {
            JSONObject object = array.getJSONObject(i);
            ContentValues values = new ContentValues();
            JSONArray names = object.names();
            if (names == null) continue;
            for (int j = 0; j < names.length(); j++) {
                String key = names.getString(j);
                Object value = object.get(key);
                if (value == JSONObject.NULL) values.putNull(key);
                else if (value instanceof Integer) values.put(key, (Integer) value);
                else if (value instanceof Long) values.put(key, (Long) value);
                else if (value instanceof Double) values.put(key, (Double) value);
                else values.put(key, String.valueOf(value));
            }
            db.insertWithOnConflict(table, null, values, SQLiteDatabase.CONFLICT_REPLACE);
        }
    }

    private void putFromCursor(ContentValues values, Cursor c, String column) {
        int index = c.getColumnIndex(column);
        if (c.isNull(index)) values.putNull(column);
        else if (c.getType(index) == Cursor.FIELD_TYPE_INTEGER) values.put(column, c.getLong(index));
        else if (c.getType(index) == Cursor.FIELD_TYPE_FLOAT) values.put(column, c.getDouble(index));
        else values.put(column, c.getString(index));
    }

    private void confirm(String title, String message, ClickAction action) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Да", (d, which) -> action.run())
                .show();
    }

    private void navigate(ClickAction action) {
        if (screenTransitioning) return;
        screenTransitioning = true;
        getWindow().getDecorView().post(() -> {
            try {
                Log.d(TAG, "navigate start");
                action.run();
            } catch (Exception e) {
                Log.e(TAG, "navigation failed", e);
                toast("Ошибка перехода: " + e.getMessage());
            } finally {
                Log.d(TAG, "navigate end");
                screenTransitioning = false;
            }
        });
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    static class Settings {
        String currency;
        String themeMode;
        int weekStartDay;
        String defaultHomePeriod;
        boolean showGrossCard;
        boolean showExpensesCard;
        boolean showNetCard;
        boolean showRemainingCard;
        boolean showNetPerHourCard;
        boolean showGoalCard;
        boolean showRecentActivity;
        String homeCardOrder;
        String remainingFormulaMode;
        String roundingMode;
        boolean notificationsEnabled;
        boolean hasCompletedOnboarding;
        boolean homeCompactMode;
    }

    static class Summary {
        double gross;
        double expenses;
        double net;
        double paid;
        double remaining;
        double hours;
        double netPerHour;
        double averageShiftNet;
        double goalTarget;
        double goalProgress;
        int shiftCount;
    }

    static class Range {
        String label;
        LocalDate start;
        LocalDate end;
        boolean all;
    }

    static class ShiftCalc {
        double hours;
        double base;
        double gross;
    }

    static class Job {
        String id;
        String name;
        String type;
        double hourlyRate;
        double fixed;
        String color;
        int archived;
    }

    static class ActivityItem {
        final String id;
        final String kind;
        final String date;
        final String title;
        final String subtitle;
        final double amount;
        final String color;

        ActivityItem(String id, String kind, String date, String title, String subtitle, double amount, String color) {
            this.id = id;
            this.kind = kind;
            this.date = date;
            this.title = title;
            this.subtitle = subtitle;
            this.amount = amount;
            this.color = color;
        }
    }

    static class DayValue {
        final String date;
        double gross;
        double expenses;
        double net;

        DayValue(String date) {
            this.date = date;
        }
    }

    static class JobValue {
        final String name;
        final String color;
        final double net;

        JobValue(String name, String color, double net) {
            this.name = name;
            this.color = color;
            this.net = net;
        }
    }

    static class Prefill {
        String kind;
        String date;
        String jobId;
        String startTime;
        String endTime;
        String breakMinutes;
        boolean isBreakPaid;
        String paymentType;
        String payoutType;
        String category;
        String hourlyRate;
        String fixedAmount;
        String amount;
        String tips;
        String bonus;
        String penalty;
        String note;
    }
}
