package com.kinexmed.domain.memory

import java.util.Calendar
import java.util.Locale
import java.util.regex.Pattern

enum class QueryIntent {
    REP_INSPECTION,
    REJECTED_REPS_LIST,
    RANGE_AGGREGATION,
    EXERCISE_FREQUENCY,
    SESSION_BREAKDOWN,
    SAFETY_MEDICAL_QUERY,
    GENERAL_STATUS
}

enum class TimeRangeType {
    TODAY,
    YESTERDAY,
    THIS_WEEK,
    THIS_MONTH,
    SPECIFIC_DATE,
    ALL_TIME
}

data class ParsedRehabQuery(
    val intent: QueryIntent,
    val timeRangeType: TimeRangeType,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val repNumber: Int? = null,
    val exerciseNameFilter: String? = null,
    val rawQuery: String
)

object RehabQueryParser {

    private val ORDINAL_MAP = mapOf(
        "first" to 1, "1st" to 1,
        "second" to 2, "2nd" to 2,
        "third" to 3, "3rd" to 3,
        "fourth" to 4, "4th" to 4,
        "fifth" to 5, "5th" to 5,
        "sixth" to 6, "6th" to 6,
        "seventh" to 7, "7th" to 7,
        "eighth" to 8, "8th" to 8,
        "ninth" to 9, "9th" to 9,
        "tenth" to 10, "10th" to 10
    )

    private val MONTH_MAP = mapOf(
        "jan" to 0, "january" to 0,
        "feb" to 1, "february" to 1,
        "mar" to 2, "march" to 2,
        "apr" to 3, "april" to 3,
        "may" to 4,
        "jun" to 5, "june" to 5,
        "jul" to 6, "july" to 6,
        "aug" to 7, "august" to 7,
        "sep" to 8, "sept" to 8, "september" to 8,
        "oct" to 9, "october" to 9,
        "nov" to 10, "november" to 10,
        "dec" to 11, "december" to 11
    )

    fun parse(query: String, nowMs: Long = System.currentTimeMillis()): ParsedRehabQuery {
        val q = query.trim().lowercase(Locale.ROOT)

        // 1. Check Medical Safety & Clinical Diagnosis Guardrails
        if (isMedicalOrDiagnosticQuery(q)) {
            val (start, end) = getTodayBounds(nowMs)
            return ParsedRehabQuery(
                intent = QueryIntent.SAFETY_MEDICAL_QUERY,
                timeRangeType = TimeRangeType.TODAY,
                startTimeMs = start,
                endTimeMs = end,
                rawQuery = query
            )
        }

        // 2. Extract Time Bounds
        val (rangeType, startMs, endMs) = extractTimeBounds(q, nowMs)

        // 3. Extract Rep Number
        val repNum = extractRepNumber(q)

        // 4. Classify Intent
        val intent = when {
            repNum != null && (q.contains("why") || q.contains("count") || q.contains("rep") || q.contains("reject") || q.contains("fail")) -> {
                QueryIntent.REP_INSPECTION
            }
            q.contains("reject") || q.contains("failed") || q.contains("invalid") || q.contains("not count") || q.contains("wasn't count") -> {
                if (repNum != null) QueryIntent.REP_INSPECTION else QueryIntent.REJECTED_REPS_LIST
            }
            q.contains("which exercise") || q.contains("most") || q.contains("frequent") || q.contains("popular") -> {
                QueryIntent.EXERCISE_FREQUENCY
            }
            q.contains("how many") || q.contains("valid rep") || (q.contains("summary") && (rangeType == TimeRangeType.THIS_WEEK || rangeType == TimeRangeType.THIS_MONTH)) -> {
                QueryIntent.RANGE_AGGREGATION
            }
            q.contains("last session") || q.contains("today's session") || q.contains("explain") || q.contains("what happened") || q.contains("latest session") -> {
                QueryIntent.SESSION_BREAKDOWN
            }
            q.contains("summary") -> {
                QueryIntent.RANGE_AGGREGATION
            }
            else -> {
                if (repNum != null) QueryIntent.REP_INSPECTION else QueryIntent.GENERAL_STATUS
            }
        }

        return ParsedRehabQuery(
            intent = intent,
            timeRangeType = rangeType,
            startTimeMs = startMs,
            endTimeMs = endMs,
            repNumber = repNum,
            exerciseNameFilter = extractExerciseName(q),
            rawQuery = query
        )
    }

    private fun isMedicalOrDiagnosticQuery(q: String): Boolean {
        val triggers = listOf(
            "diagnos", "disease", "arthritis", "torn acl", "meniscus", "fracture",
            "prescribe", "medication", "medicine", "pill", "ibuprofen", "cure",
            "am i healed", "is it healed", "treatment plan", "medical advice"
        )
        return triggers.any { q.contains(it) }
    }

    private fun extractTimeBounds(q: String, nowMs: Long): Triple<TimeRangeType, Long, Long> {
        val cal = Calendar.getInstance().apply { timeInMillis = nowMs }

        if (q.contains("yesterday")) {
            cal.add(Calendar.DAY_OF_YEAR, -1)
            val (start, end) = getDayBounds(cal)
            return Triple(TimeRangeType.YESTERDAY, start, end)
        }

        if (q.contains("today")) {
            val (start, end) = getDayBounds(cal)
            return Triple(TimeRangeType.TODAY, start, end)
        }

        if (q.contains("week")) {
            cal.add(Calendar.DAY_OF_YEAR, -7)
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            return Triple(TimeRangeType.THIS_WEEK, cal.timeInMillis, nowMs)
        }

        if (q.contains("month")) {
            cal.set(Calendar.DAY_OF_MONTH, 1)
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            return Triple(TimeRangeType.THIS_MONTH, cal.timeInMillis, nowMs)
        }

        // Check for specific date (e.g., "September 20", "Sep 20", "20 September")
        for ((monthName, monthIndex) in MONTH_MAP) {
            val pattern1 = Pattern.compile("\\b$monthName\\s+(\\d{1,2})\\b")
            val matcher1 = pattern1.matcher(q)
            if (matcher1.find()) {
                val day = matcher1.group(1)?.toIntOrNull() ?: 1
                return buildSpecificDateRange(cal, monthIndex, day)
            }

            val pattern2 = Pattern.compile("\\b(\\d{1,2})(?:st|nd|rd|th)?\\s+(?:of\\s+)?$monthName\\b")
            val matcher2 = pattern2.matcher(q)
            if (matcher2.find()) {
                val day = matcher2.group(1)?.toIntOrNull() ?: 1
                return buildSpecificDateRange(cal, monthIndex, day)
            }
        }

        // Default to all time (or wide window)
        return Triple(TimeRangeType.ALL_TIME, 0L, nowMs)
    }

    private fun buildSpecificDateRange(currentCal: Calendar, monthIndex: Int, day: Int): Triple<TimeRangeType, Long, Long> {
        val targetCal = Calendar.getInstance().apply {
            timeInMillis = currentCal.timeInMillis
            set(Calendar.MONTH, monthIndex)
            set(Calendar.DAY_OF_MONTH, day)
        }
        val (start, end) = getDayBounds(targetCal)
        return Triple(TimeRangeType.SPECIFIC_DATE, start, end)
    }

    private fun getDayBounds(cal: Calendar): Pair<Long, Long> {
        val startCal = Calendar.getInstance().apply {
            timeInMillis = cal.timeInMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val endCal = Calendar.getInstance().apply {
            timeInMillis = cal.timeInMillis
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }
        return Pair(startCal.timeInMillis, endCal.timeInMillis)
    }

    private fun getTodayBounds(nowMs: Long): Pair<Long, Long> {
        val cal = Calendar.getInstance().apply { timeInMillis = nowMs }
        return getDayBounds(cal)
    }

    private fun extractRepNumber(q: String): Int? {
        for ((word, num) in ORDINAL_MAP) {
            if (q.contains(word)) return num
        }

        val patterns = listOf(
            Pattern.compile("rep(?:etition)?\\s*(?:#|number|no\\.?)?\\s*(\\d+)"),
            Pattern.compile("(\\d+)(?:st|nd|rd|th)?\\s+rep")
        )

        for (p in patterns) {
            val m = p.matcher(q)
            if (m.find()) {
                val num = m.group(1)?.toIntOrNull()
                if (num != null) return num
            }
        }

        return null
    }

    private fun extractExerciseName(q: String): String? {
        val names = listOf(
            "squat", "sit to stand", "sit-to-stand", "lunge", "calf raise",
            "knee extension", "hip abduction", "hip extension", "marching",
            "shoulder flexion", "shoulder abduction", "elbow flexion", "elbow extension",
            "bicep curl", "balance"
        )
        return names.firstOrNull { q.contains(it) }
    }
}
