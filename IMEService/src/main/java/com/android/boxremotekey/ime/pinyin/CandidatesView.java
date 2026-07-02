package com.android.boxremotekey.ime.pinyin;

import android.content.Context;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

public class CandidatesView {

    private static final String TAG = "CandidatesView";

    private final LinearLayout mRoot;
    private final TextView mPinyinText;
    private final LinearLayout mButtonContainer;
    private CandidateSelectListener mSelectListener;
    private PageFlipListener mPageFlipListener;

    public interface CandidateSelectListener {
        void onCandidateSelected(String character);
    }

    public interface PageFlipListener {
        void onPageFlip();
    }

    public CandidatesView(Context context) {
        mRoot = new LinearLayout(context);
        mRoot.setOrientation(LinearLayout.HORIZONTAL);
        mRoot.setGravity(Gravity.CENTER_VERTICAL);
        mRoot.setVisibility(View.GONE);
        mRoot.setBackgroundColor(0xFFE0E0E0);
        int pad = dpToPx(context, 6);
        mRoot.setPadding(pad, pad, pad, pad);
        mRoot.setFocusable(false);

        mPinyinText = new TextView(context);
        mPinyinText.setTextColor(0xFF0061A4);
        mPinyinText.setTextSize(16);
        mPinyinText.setPadding(pad, 0, pad, 0);
        mPinyinText.setFocusable(false);
        LinearLayout.LayoutParams pinyinLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pinyinLp.gravity = Gravity.CENTER_VERTICAL;
        mPinyinText.setLayoutParams(pinyinLp);
        mRoot.addView(mPinyinText);

        mButtonContainer = new LinearLayout(context);
        mButtonContainer.setOrientation(LinearLayout.HORIZONTAL);
        mButtonContainer.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        mButtonContainer.setLayoutParams(btnLp);
        mRoot.addView(mButtonContainer);
    }

    public View getView() {
        return mRoot;
    }

    public void setSelectListener(CandidateSelectListener listener) {
        mSelectListener = listener;
    }

    public void setPageFlipListener(PageFlipListener listener) {
        mPageFlipListener = listener;
    }

    public void show() {
        mRoot.setVisibility(View.VISIBLE);
    }

    public void hide() {
        mRoot.setVisibility(View.GONE);
    }

    public boolean isShown() {
        return mRoot.getVisibility() == View.VISIBLE;
    }

    public void setPinyin(String pinyin) {
        mPinyinText.setText(pinyin);
    }

    public void setCandidates(List<String> candidates, int page, int totalPages, int highlightIndex) {
        Log.d(TAG, "setCandidates: " + (candidates != null ? candidates.size() : 0)
                + " items, page=" + page + "/" + totalPages);
        mButtonContainer.removeAllViews();

        Context context = mRoot.getContext();
        if (candidates == null || candidates.isEmpty()) return;

        int maxShow = Math.min(candidates.size(), 9);

        for (int i = 0; i < maxShow; i++) {
            final String candidate = candidates.get(i);
            final int pos = i + 1;

            Button btn = new Button(context, null, android.R.attr.buttonBarButtonStyle);
            btn.setText(pos + "." + candidate);
            btn.setTextSize(14);
            btn.setTextColor(0xFF1A1C1E);
            btn.setBackgroundResource(com.android.boxremotekey.R.drawable.kb_key_normal);
            btn.setMinWidth(dpToPx(context, 48));
            btn.setMinimumWidth(dpToPx(context, 48));
            btn.setPadding(dpToPx(context, 6), dpToPx(context, 4),
                    dpToPx(context, 6), dpToPx(context, 4));
            btn.setAllCaps(false);
            btn.setFocusable(true);
            btn.setFocusableInTouchMode(true);
            btn.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View v, boolean hasFocus) {
                    v.setSelected(hasFocus);
                }
            });

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(dpToPx(context, 2), 0, dpToPx(context, 2), 0);
            btn.setLayoutParams(lp);

            btn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (mSelectListener != null) {
                        mSelectListener.onCandidateSelected(candidate);
                    }
                }
            });

            mButtonContainer.addView(btn);
        }

        if (totalPages > 1) {
            Button pageBtn = new Button(context, null, android.R.attr.buttonBarButtonStyle);
            pageBtn.setText((page + 1) + "/" + totalPages + " ▶");
            pageBtn.setTextSize(12);
            pageBtn.setTextColor(0xFF0061A4);
            pageBtn.setBackgroundResource(com.android.boxremotekey.R.drawable.kb_key_alt);
            pageBtn.setMinWidth(dpToPx(context, 36));
            pageBtn.setMinimumWidth(dpToPx(context, 36));
            pageBtn.setPadding(dpToPx(context, 4), dpToPx(context, 4),
                    dpToPx(context, 4), dpToPx(context, 4));
            pageBtn.setAllCaps(false);
            pageBtn.setFocusable(true);
            pageBtn.setFocusableInTouchMode(true);
            pageBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (mPageFlipListener != null) {
                        mPageFlipListener.onPageFlip();
                    }
                }
            });

            mButtonContainer.addView(pageBtn);
        }
    }

    private int dpToPx(Context context, int dp) {
        float density = context.getResources().getDisplayMetrics().density;
        return (int) (dp * density + 0.5f);
    }
}
