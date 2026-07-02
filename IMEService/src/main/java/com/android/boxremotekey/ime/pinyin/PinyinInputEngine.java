package com.android.boxremotekey.ime.pinyin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PinyinInputEngine {

    private static final int PAGE_SIZE = 9;

    public interface CandidateListener {
        void onPinyinChanged(String pinyin);
        void onCandidatesChanged(List<String> candidates, int page, int totalPages, int highlightIndex);
    }

    private final PinyinDictionary mDictionary;
    private final StringBuilder mPinyinBuffer = new StringBuilder();
    private List<String> mCandidates = Collections.emptyList();
    private int mCurrentPage = 0;
    private int mHighlightIndex = 0;
    private CandidateListener mListener;

    public PinyinInputEngine(PinyinDictionary dictionary) {
        mDictionary = dictionary;
    }

    public void setListener(CandidateListener listener) {
        mListener = listener;
    }

    public void processLetter(char c) {
        char lower = Character.toLowerCase(c);
        if (lower < 'a' || lower > 'z') return;
        mPinyinBuffer.append(lower);
        String pinyin = mPinyinBuffer.toString();
        notifyPinyinChanged(pinyin);
        updateCandidates(pinyin);
    }

    public void processBackspace() {
        if (mPinyinBuffer.length() > 0) {
            mPinyinBuffer.deleteCharAt(mPinyinBuffer.length() - 1);
            String pinyin = mPinyinBuffer.toString();
            notifyPinyinChanged(pinyin);
            if (pinyin.isEmpty()) {
                mCandidates = Collections.emptyList();
                mCurrentPage = 0;
                mHighlightIndex = 0;
                notifyCandidatesChanged();
            } else {
                updateCandidates(pinyin);
            }
        }
    }

    public String getHighlightedCandidate() {
        int globalIdx = mCurrentPage * PAGE_SIZE + mHighlightIndex;
        if (globalIdx >= 0 && globalIdx < mCandidates.size()) {
            return mCandidates.get(globalIdx);
        }
        return null;
    }

    public String getSelectedCandidate(int index) {
        if (index >= 0 && index < mCandidates.size()) {
            return mCandidates.get(index);
        }
        return null;
    }

    public String getFirstCandidate() {
        return mCandidates.isEmpty() ? null : mCandidates.get(0);
    }

    public int getCandidateCount() {
        return mCandidates.size();
    }

    public int getTotalPages() {
        return mCandidates.isEmpty() ? 0 : (int) Math.ceil((double) mCandidates.size() / PAGE_SIZE);
    }

    public int getCurrentPage() {
        return mCurrentPage;
    }

    public int getHighlightIndex() {
        return mHighlightIndex;
    }

    public List<String> getPageCandidates() {
        int start = mCurrentPage * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, mCandidates.size());
        if (start >= mCandidates.size()) return Collections.emptyList();
        return mCandidates.subList(start, end);
    }

    public void highlightNext() {
        if (mCandidates.isEmpty()) return;
        List<String> page = getPageCandidates();
        if (mHighlightIndex < page.size() - 1) {
            mHighlightIndex++;
        } else if (mCurrentPage < getTotalPages() - 1) {
            mCurrentPage++;
            mHighlightIndex = 0;
        }
        notifyCandidatesChanged();
    }

    public void highlightPrev() {
        if (mCandidates.isEmpty()) return;
        if (mHighlightIndex > 0) {
            mHighlightIndex--;
        } else if (mCurrentPage > 0) {
            mCurrentPage--;
            mHighlightIndex = getPageCandidates().size() - 1;
        }
        notifyCandidatesChanged();
    }

    public void nextPage() {
        int total = getTotalPages();
        if (total > 1) {
            mCurrentPage = (mCurrentPage + 1) % total;
            mHighlightIndex = 0;
            notifyCandidatesChanged();
        }
    }

    public void prevPage() {
        int total = getTotalPages();
        if (total > 1) {
            mCurrentPage = (mCurrentPage - 1 + total) % total;
            mHighlightIndex = 0;
            notifyCandidatesChanged();
        }
    }

    public int selectCandidateByNumber(int num) {
        int pageCandidates = getPageCandidates().size();
        int idx;
        if (num == 0) {
            idx = pageCandidates - 1;
        } else {
            idx = Math.min(num, pageCandidates) - 1;
        }
        if (idx >= 0 && idx < pageCandidates) {
            return mCurrentPage * PAGE_SIZE + idx;
        }
        return -1;
    }

    public List<String> getCandidates() {
        return mCandidates;
    }

    public String getPinyin() {
        return mPinyinBuffer.toString();
    }

    public void clear() {
        mPinyinBuffer.setLength(0);
        mCandidates = Collections.emptyList();
        mCurrentPage = 0;
        mHighlightIndex = 0;
        notifyPinyinChanged("");
        notifyCandidatesChanged();
    }

    public boolean hasInput() {
        return mPinyinBuffer.length() > 0;
    }

    private void updateCandidates(String pinyin) {
        List<String> candidates = mDictionary.lookup(pinyin);
        if (candidates.isEmpty()) {
            candidates = splitPinyinLookup(pinyin);
        }
        if (candidates.isEmpty()) {
            candidates = fuzzyLookup(pinyin);
        }
        mCandidates = candidates;
        mCurrentPage = 0;
        mHighlightIndex = 0;
        notifyCandidatesChanged();
    }

    private List<String> fuzzyLookup(String pinyin) {
        List<String> result = new ArrayList<>();
        int len = pinyin.length();

        if (len == 1) {
            return mDictionary.fuzzyLookupByPrefix(pinyin);
        }

        for (int split = 1; split < len; split++) {
            String first = pinyin.substring(0, split);
            String second = pinyin.substring(split);
            List<String> firstChars = getFuzzyCandidates(first);
            List<String> secondChars = getFuzzyCandidates(second);
            if (!firstChars.isEmpty() && !secondChars.isEmpty()) {
                int limit = Math.min(firstChars.size(), 5);
                for (int i = 0; i < limit; i++) {
                    int limit2 = Math.min(secondChars.size(), 4);
                    for (int j = 0; j < limit2; j++) {
                        result.add(firstChars.get(i) + secondChars.get(j));
                        if (result.size() >= 20) return result;
                    }
                }
            }
        }
        return result;
    }

    private List<String> getFuzzyCandidates(String key) {
        List<String> exact = mDictionary.lookup(key);
        if (!exact.isEmpty()) return exact;
        return mDictionary.fuzzyLookupByPrefix(key);
    }

    private List<String> splitPinyinLookup(String pinyin) {
        List<String> result = new ArrayList<>();
        int len = pinyin.length();
        int split = len - 1;
        while (split > 0) {
            String first = pinyin.substring(0, split);
            String second = pinyin.substring(split);
            List<String> firstChars = mDictionary.lookup(first);
            List<String> secondChars = mDictionary.lookup(second);
            if (!firstChars.isEmpty() && !secondChars.isEmpty()) {
                for (String f : firstChars) {
                    for (String s : secondChars) {
                        result.add(f + s);
                        if (result.size() >= 20) return result;
                    }
                }
                if (!result.isEmpty()) return result;
            }
            split--;
        }
        return result;
    }

    private void notifyPinyinChanged(String pinyin) {
        if (mListener != null) {
            mListener.onPinyinChanged(pinyin);
        }
    }

    private void notifyCandidatesChanged() {
        if (mListener != null) {
            List<String> page = getPageCandidates();
            mListener.onCandidatesChanged(page, mCurrentPage, getTotalPages(), mHighlightIndex);
        }
    }
}
