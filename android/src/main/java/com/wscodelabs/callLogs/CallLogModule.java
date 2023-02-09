package com.wscodelabs.callLogs;

import android.provider.CallLog.Calls;
import android.database.Cursor;
import android.content.Context;
// import android.provider.Telephony;
// import android.telecom.PhoneAccount;
// import android.telecom.PhoneAccountHandle;
// import android.telecom.TelecomManager;
// import android.telephony.SubscriptionManager;
// import android.telephony.SubscriptionInfo;
// import android.telephony.TelephonyManager;

// import android.util.Log;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;

import org.json.JSONArray;
import org.json.JSONException;

import javax.annotation.Nullable;

public class CallLogModule extends ReactContextBaseJavaModule {

    private Context context;

    public CallLogModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.context = reactContext;
    }

    @Override
    public String getName() {
        return "CallLogs";
    }

    @ReactMethod
    public void loadAll(Promise promise) {
        load(-1, promise);
    }

    @ReactMethod
    public void load(int limit, Promise promise) {
        loadWithFilter(limit, null, promise);
    }

    @ReactMethod
    public void loadWithFilter(int limit, @Nullable ReadableMap filter, Promise promise) {
        try {
            Cursor cursor = this.context.getContentResolver().query(
                    Calls.CONTENT_URI,
                    new String[]{Calls.DATE, Calls.TYPE, Calls.NUMBER, Calls.DURATION, Calls.PHONE_ACCOUNT_ID},
                    null,
                    null,
                    Calls.DATE + " DESC"
            );

            WritableArray result = Arguments.createArray();

            if (cursor == null) {
                promise.resolve(result);
                return;
            }

            boolean nullFilter = filter == null;
            String minTimestamp = !nullFilter && filter.hasKey("minTimestamp") ? filter.getString("minTimestamp") : "0";
            String maxTimestamp = !nullFilter && filter.hasKey("maxTimestamp") ? filter.getString("maxTimestamp") : "-1";

            String types = !nullFilter && filter.hasKey("types") ? filter.getString("types") : "[]";
            JSONArray typesArray= new JSONArray(types);
            Set<String> typeSet = new HashSet<>(Arrays.asList(toStringArray(typesArray)));

            String phoneNumbers = !nullFilter && filter.hasKey("phoneNumbers") ? filter.getString("phoneNumbers") : "[]";
            JSONArray phoneNumbersArray= new JSONArray(phoneNumbers);
            Set<String> phoneNumberSet = new HashSet<>(Arrays.asList(toStringArray(phoneNumbersArray)));

            int callLogCount = 0;

            final int NUMBER_COLUMN_INDEX = cursor.getColumnIndex(Calls.NUMBER);
            final int TYPE_COLUMN_INDEX = cursor.getColumnIndex(Calls.TYPE);
            final int DATE_COLUMN_INDEX = cursor.getColumnIndex(Calls.DATE);
            final int DURATION_COLUMN_INDEX = cursor.getColumnIndex(Calls.DURATION);
            final int PHONE_ACCOUNT_ID_INDEX = cursor.getColumnIndex(Calls.PHONE_ACCOUNT_ID);

            boolean minTimestampDefined = minTimestamp != null && !minTimestamp.equals("0");
            boolean minTimestampReached = false;

            while (cursor.moveToNext() && this.shouldContinue(limit, callLogCount) && !minTimestampReached) {
                String phoneNumber = cursor.getString(NUMBER_COLUMN_INDEX);
                String phoneAccountId = cursor.getString(PHONE_ACCOUNT_ID_INDEX);
                int duration = cursor.getInt(DURATION_COLUMN_INDEX);

                String timestampStr = cursor.getString(DATE_COLUMN_INDEX);
                minTimestampReached = minTimestampDefined && Long.parseLong(timestampStr) <= Long.parseLong(minTimestamp);

                DateFormat df = SimpleDateFormat.getDateTimeInstance(SimpleDateFormat.MEDIUM, SimpleDateFormat.MEDIUM);
                //DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                String dateTime = df.format(new Date(Long.valueOf(timestampStr)));

                String type = this.resolveCallType(cursor.getInt(TYPE_COLUMN_INDEX));

                boolean passesPhoneFilter = phoneNumberSet == null || phoneNumberSet.isEmpty() || phoneNumberSet.contains(phoneNumber);
                boolean passesTypeFilter = typeSet == null || typeSet.isEmpty() || typeSet.contains(type);
                boolean passesMinTimestampFilter = minTimestamp == null || minTimestamp.equals("0") || Long.parseLong(timestampStr) >= Long.parseLong(minTimestamp);
                boolean passesMaxTimestampFilter = maxTimestamp == null || maxTimestamp.equals("-1") || Long.parseLong(timestampStr) <= Long.parseLong(maxTimestamp);
                boolean passesFilter = passesPhoneFilter && passesTypeFilter && passesMinTimestampFilter && passesMaxTimestampFilter;

                if (passesFilter) {
                    WritableMap callLog = Arguments.createMap();
                    callLog.putString("phoneNumber", phoneNumber);
                    callLog.putInt("duration", duration);
                    callLog.putString("timestamp", timestampStr);
                    callLog.putString("dateTime", dateTime);
                    callLog.putString("type", type);
                    callLog.putString("phoneAccountId", phoneAccountId);
                    result.pushMap(callLog);
                    callLogCount++;
                }
            }

            cursor.close();

            promise.resolve(result);
        } catch (JSONException e) {
            promise.reject(e);
        }
    }

    @ReactMethod
    public void getActiveSimCount(Promise promise) {
        SubscriptionManager subscription = (SubscriptionManager) context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
        int count = subscription.getActiveSubscriptionInfoCount();
//        List list =  asd.getActiveSubscriptionInfoList();
        promise.resolve(count);
    }

    @ReactMethod
    public void getCallLogs() {
        // paginated list of calllogs.
    }

    @ReactMethod
    public void getLastRowId(Promise promise) {
        Uri uri = Calls.CONTENT_URI.buildUpon().appendQueryParameter(Calls.LIMIT_PARAM_KEY, "1").build();

        Cursor cursor = this.context.getContentResolver().query(
                uri,
                new String[]{Calls._ID},
                null,
                null,
                Calls.DEFAULT_SORT_ORDER
        );
        if (cursor == null) {
            promise.resolve(-1);
            return;
        }

        cursor.moveToFirst();
        int result = cursor.getInt(0);
        cursor.close();
        promise.resolve(result);
    }

    @ReactMethod
    public void getTotalDurationOfTheDay(String timeStamp, String callType, Promise promise) {
        long[] timestamps = getDayTimestamps(Long.parseLong(timeStamp));
        long startTimestamp = timestamps[0];
        long endTimestamp = timestamps[1];

        int callTypeCode = resolveCallType(callType);

        // String[] projection = new String[] { "SELECT SUM(" + Calls.DURATION + ") as total_duration"};
        String[] projection = new String[] {Calls.DURATION};
        String selection = Calls.TYPE + " = " + callTypeCode + " AND " + Calls.DATE + " BETWEEN " + startTimestamp + " AND " + endTimestamp;
        Cursor cursor = context.getContentResolver().query(Calls.CONTENT_URI, projection, selection, null, null);

        if (cursor == null || cursor.getCount() == 0 ) {
            promise.resolve(0);
        }

        int total_duration = 0;
        // if (cursor.moveToFirst()) {
        //     int idx = cursor.getColumnIndex("total_duration");
        //     total_duration = cursor.getLong(idx);
        // }
        final int DURATION_INDEX = cursor.getColumnIndex(Calls.DURATION);
        while (cursor.moveToNext()) {
            total_duration = total_duration + cursor.getInt(DURATION_INDEX);
        }

        cursor.close();
        promise.resolve(total_duration);
    }


    @ReactMethod
    public void getTotalDurationDayWise(String startDate, String endDate, String callType, Promise promise) {
        // String startDate = "2022-01-01";
        // String endDate = "2022-01-31";

        int callTypeCode = resolveCallType(callType);

        String[] projection = new String[] { "SUM("+Calls.DURATION+") as total_duration", "date("+Calls.DATE+"/1000, 'unixepoch', 'localtime') as date_group"};
        // TODO: check if endDate and startDate need to be converted to unix timestamp
        String selection =  Calls.TYPE + " = " + callTypeCode
                + " AND " + Calls.DATE + " >= " + startDate + " AND " + Calls.DATE + " <= " + endDate;

        String groupBy = "date_group";

        Cursor cursor = context.getContentResolver().query(
                Calls.CONTENT_URI,
                projection,
                selection,
                null,
                groupBy
        );

        if (cursor == null || cursor.getCount() == 0 ) {
            // promise.reject(null);
        }

        WritableArray result = Arguments.createArray();
        WritableMap callLog = Arguments.createMap();

        HashMap<String, String> dateDurationMap = new HashMap<>();
        while (cursor.moveToNext()) {
            String dateGroup = cursor.getString(cursor.getColumnIndex("date_group"));
            String totalDuration = String.valueOf(cursor.getLong(cursor.getColumnIndex("total_duration")));
            dateDurationMap.put(dateGroup, totalDuration);
        }

        cursor.close();
        promise.resolve(dateDurationMap);
    }


    public static String[] toStringArray(JSONArray array) {
        if(array==null)
            return null;

        String[] arr=new String[array.length()];
        for(int i=0; i<arr.length; i++) {
            arr[i]=array.optString(i);
        }
        return arr;
    }

    private String resolveCallType(int callTypeCode) {
        switch (callTypeCode) {
            case Calls.OUTGOING_TYPE:
                return "OUTGOING";
            case Calls.INCOMING_TYPE:
                return "INCOMING";
            case Calls.MISSED_TYPE:
                return "MISSED";
            case Calls.VOICEMAIL_TYPE:
                return "VOICEMAIL";
            case Calls.REJECTED_TYPE:
                return "REJECTED";
            case Calls.BLOCKED_TYPE:
                return "BLOCKED";
            case Calls.ANSWERED_EXTERNALLY_TYPE:
                return "ANSWERED_EXTERNALLY";
            default:
                return "UNKNOWN";
        }
    }

    private int resolveCallType(String callType) {
        switch (callType) {
            case "OUTGOING":
                return Calls.OUTGOING_TYPE;
            case "INCOMING":
                return Calls.INCOMING_TYPE;
            case "MISSED":
                return Calls.MISSED_TYPE;
            default:
                return -1;
        }
    }

    private boolean shouldContinue(int limit, int count) {
        return limit < 0 || count < limit;
    }

    private static long[] getDayTimestamps(long timestamp) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(timestamp);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        long startTimestamp = calendar.getTimeInMillis();
        calendar.add(Calendar.DAY_OF_MONTH, 1);
        calendar.add(Calendar.MILLISECOND, -1);
        long endTimestamp = calendar.getTimeInMillis();
        return new long[] {startTimestamp, endTimestamp};
    }

    // private int getSimSlotIndexFromAccountId(Context context, String accountIdToFind) {
    //     /**
    //      * This code is checking for the sim card slot index by using the accountId passed by the user,
    //      * it does it by first getting the Telecom manager service and then getting the call capable phone accounts.
    //      * Then it iterates over the phone accounts, it gets the account handle and id of each phone account,
    //      * compares it with the accountId passed by the user, if it matches it returns the index of the account.
    //      * If the accountId passed by the user is not matched then it tries to convert it to int
    //      * and checks if it is greater than or equal to 0,
    //      * if it is then it returns that int as the index otherwise it returns -1,
    //      * indicating that it didn't find any match.
    //      */

    //     TelecomManager telecomManager = (TelecomManager) context.getSystemService(Context.TELECOM_SERVICE);
    //     List<PhoneAccountHandle> phoneAccounts = telecomManager.getCallCapablePhoneAccounts();

    //     for (int i = 0; i < phoneAccounts.size(); i++) {
    //         PhoneAccountHandle account = phoneAccounts.get(i);
    //         PhoneAccount phoneAccount = telecomManager.getPhoneAccount(account);
    //         String accountId = phoneAccount.getAccountHandle().getId();

    //         if (accountIdToFind.equals(accountId)) {
    //             return i;
    //         }
    //     }
    //     Integer accountId = Integer.parseInt(accountIdToFind);
    //     if (accountId != null && accountId >= 0) {
    //         return accountId;
    //     }
    //     return -1;
    // }
}

