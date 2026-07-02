package com.android.boxremotekey.ime.pinyin;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map;

public class PinyinDictionary {

    private static final String TAG = "PinyinDictionary";
    private final Map<String, List<String>> mDictionary = new HashMap<>();

    public PinyinDictionary(Context context) {
        loadDictionary(context);
    }

    private void loadDictionary(Context context) {
        try {
            int resId = com.android.boxremotekey.R.raw.pinyin_dict;
            InputStream is = context.getResources().openRawResource(resId);
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
            String line;
            int count = 0;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                int colon = line.indexOf(':');
                if (colon < 0) continue;
                String pinyin = line.substring(0, colon).trim().toLowerCase();
                String charsStr = line.substring(colon + 1).trim();
                if (pinyin.isEmpty() || charsStr.isEmpty()) continue;
                List<String> chars = new ArrayList<>();
                for (String c : charsStr.split(",")) {
                    c = c.trim();
                    if (!c.isEmpty()) {
                        chars.add(c);
                    }
                }
                if (!chars.isEmpty()) {
                    mDictionary.put(pinyin, chars);
                    count++;
                }
            }
            reader.close();
            Log.i(TAG, "Dictionary loaded: " + count + " pinyin entries, " + mDictionary.get("ni"));
        } catch (Exception e) {
            Log.e(TAG, "Failed to load pinyin dictionary", e);
        }
    }

    public List<String> lookup(String pinyin) {
        if (pinyin == null || pinyin.isEmpty()) return Collections.emptyList();
        List<String> result = mDictionary.get(pinyin.toLowerCase());
        if (result != null) {
            Log.d(TAG, "lookup(" + pinyin + ") = " + result.size() + " candidates");
        }
        return result != null ? result : Collections.<String>emptyList();
    }

    public boolean hasPinyin(String pinyin) {
        return pinyin != null && mDictionary.containsKey(pinyin.toLowerCase());
    }

    public int size() {
        return mDictionary.size();
    }

    public List<String> fuzzyLookupByPrefix(String prefix) {
        if (prefix == null || prefix.isEmpty()) return Collections.emptyList();
        List<String> result = new ArrayList<>();
        String key = prefix.toLowerCase();
        for (Map.Entry<String, List<String>> e : mDictionary.entrySet()) {
            if (e.getKey().startsWith(key)) {
                result.addAll(e.getValue());
                if (result.size() >= 18) break;
            }
        }
        return result;
    }
}
