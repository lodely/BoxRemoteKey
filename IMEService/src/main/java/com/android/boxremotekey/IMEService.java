package com.android.boxremotekey;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.inputmethodservice.InputMethodService;
import android.os.Handler;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.boxremotekey.ime.pinyin.CandidatesView;
import com.android.boxremotekey.ime.pinyin.PinyinDictionary;
import com.android.boxremotekey.ime.pinyin.PinyinInputEngine;
import com.android.boxremotekey.server.RemoteServer;
import com.android.boxremotekey.server.RemoteServerFileManager;
import com.android.boxremotekey.adb.AdbHelper;

import java.io.IOException;
import java.util.List;


public class IMEService extends InputMethodService implements View.OnClickListener{
	public static String TAG = "BoxRemoteKey";
	public static String ACTION = "com.android.boxremotekey";

	private boolean capsOn = false;
	private Button btnCaps = null;
	private View focusedView = null;
	private ViewGroup mInputView = null;
	private boolean hideWindowByKey = false;

	private View helpDialog = null;
	private ImageView qrCodeImage = null;
	private TextView  addressView = null;

	private RemoteServer mServer = null;
	private LinearLayout row1 = null;
	private LinearLayout row2 = null;
	private LinearLayout row3 = null;
	private LinearLayout row4 = null;

	private static final int SERVER_START_ERROR = 901;
	private static final int ERROR = 999;
	private static final int TOAST_MESSAGE = 1000;

	public static final int KEY_ACTION_PRESSED = 0;
	public static final int KEY_ACTION_DOWN = 1;
	public static final int KEY_ACTION_UP = 2;

	// Chinese input mode
	private boolean mChineseMode = false;
	private Button mBtnToggleChinese = null;
	private PinyinDictionary mPinyinDictionary = null;
	private PinyinInputEngine mPinyinEngine = null;
	private CandidatesView mCandidatesView = null;
	private FrameLayout mCandidatesFrame = null;

	// Symbol page
	private boolean mSymbolPage = false;
	private Button mBtnToggleSymbol = null;

	// ABC letters only (26 chars, 7+7+7+5 across 4 rows)
	private static final String[] ABC_ROW1 = {"A","B","C","D","E","F","G"};
	private static final String[] ABC_ROW2 = {"H","I","J","K","L","M","N"};
	private static final String[] ABC_ROW3 = {"O","P","Q","R","S","T","U"};
	private static final String[] ABC_ROW4 = {"V","W","X","Y","Z"};
	private static final String[] SYM_ROW1  = {"【","】","：","；","！","@","+"};
	private static final String[] SYM_ROW2  = {"%","|","&","*","(",")","—"};
	private static final String[] SYM_ROW3  = {"=","{","}","\"","'",";",":"};
	private static final String[] SYM_ROW4  = {"`","~","^",",","、"};

	final Handler handler = new Handler();

	@Override
	public void onCreate() {
		super.onCreate();

		Environment.initToastHandler();

		RemoteServerFileManager.resetBaseDir(this);
		startRemoteServer();
		DLNAUtils.startDLNAService(this.getApplicationContext());
		new AutoUpdateManager(this, this.handler);

		mPinyinDictionary = new PinyinDictionary(this);
		mPinyinEngine = new PinyinInputEngine(mPinyinDictionary);
	}

	@Override
    public View onCreateInputView()  {
		if(Environment.needDebug){
			Environment.debug(TAG, "onCreateInputView.");
		}
    	mInputView = (ViewGroup)getLayoutInflater().inflate(R.layout.keyboard, null);

		capsOn = true;
		btnCaps = mInputView.findViewById(R.id.btnCaps);
		row1 = mInputView.findViewById(R.id.row1);
		row2 = mInputView.findViewById(R.id.row2);
		row3 = mInputView.findViewById(R.id.row3);
		row4 = mInputView.findViewById(R.id.row4);

		helpDialog = mInputView.findViewById(R.id.helpDialog);
		qrCodeImage = helpDialog.findViewById(R.id.ivQRCode);
		addressView = helpDialog.findViewById(R.id.tvAddress);

		mBtnToggleChinese = mInputView.findViewById(R.id.btnToggleChinese);
		mBtnToggleSymbol = mInputView.findViewById(R.id.btnToggleSymbol);

		// QR area click listener on the image only
		View qrArea = mInputView.findViewById(R.id.qrArea);
		final ImageView ivQrThumb = mInputView.findViewById(R.id.ivQrThumb);
		final View qrThumbContainer = mInputView.findViewById(R.id.qrThumbContainer);
		ivQrThumb.setImageResource(R.drawable.net_icon);
		qrThumbContainer.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				showHelpDialog();
			}
		});
		// Generate real QR code thumbnail after server starts
		handler.postDelayed(new Runnable() {
			public void run() {
				if (mServer != null && mServer.getServerAddress() != null) {
					ivQrThumb.setImageBitmap(QRCodeGen.generateBitmap(mServer.getServerAddress(), 160, 160));
				}
			}
		}, 1500);

		// Initialize CandidatesView
		mCandidatesFrame = mInputView.findViewById(R.id.candidatesFrame);
		mCandidatesView = new CandidatesView(this);
		mCandidatesView.setSelectListener(new CandidatesView.CandidateSelectListener() {
			@Override
			public void onCandidateSelected(String character) {
				commitText(character);
				mPinyinEngine.clear();
				mCandidatesView.hide();
				mCandidatesFrame.setVisibility(View.GONE);
				// Return focus to first key of keyboard
				LinearLayout firstRow = findFirstKeyRow();
				if (firstRow != null) {
					focusedView = findFocusableChildAt(firstRow, 0, true);
					if (focusedView != null) {
						focusedView.requestFocus();
						focusedView.requestFocusFromTouch();
					}
				}
			}
		});
		mCandidatesView.setPageFlipListener(new CandidatesView.PageFlipListener() {
			@Override
			public void onPageFlip() {
				mPinyinEngine.nextPage();
			}
		});
		mCandidatesFrame.addView(mCandidatesView.getView());

		mPinyinEngine.setListener(new PinyinInputEngine.CandidateListener() {
			@Override
			public void onPinyinChanged(String pinyin) {
				mCandidatesView.setPinyin(pinyin);
				if (!pinyin.isEmpty()) {
					mCandidatesFrame.setVisibility(View.VISIBLE);
					mCandidatesView.show();
				}
			}

			@Override
			public void onCandidatesChanged(List<String> candidates, int page, int totalPages, int highlightIndex) {
				mCandidatesView.setCandidates(candidates, page, totalPages, highlightIndex);
			}
		});

		// Set click listeners for ImageButtons (none left, all are Buttons now)
		// All key buttons use android:onClick="onClick" from their style

		toggleCapsState(true);

        return mInputView; 
    }

	@Override
	public void onStartInputView(EditorInfo attribute, boolean restarting) {
		super.onStartInputView(attribute, restarting);
		if (mInputView != null) {
			LinearLayout firstRow = findFirstKeyRow();
			if (firstRow != null && firstRow.getChildCount() > 0) {
				focusedView = firstRow.getChildAt(0);
				focusedView.requestFocus();
				focusedView.requestFocusFromTouch();
			}
		}
	}

	@Override
	public View onCreateCandidatesView() {
		return null;
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		onStart(intent, startId);
		return START_STICKY;
	}

	@Override
	public void onStartInput(EditorInfo attribute, boolean restarting) {
		super.onStartInput(attribute, restarting);
	}

	@Override
	public void onFinishInputView(boolean finishingInput) {
		if (mChineseMode && mBtnToggleChinese != null) {
			mChineseMode = false;
			mBtnToggleChinese.setText("中");
		}
		if (mPinyinEngine != null) mPinyinEngine.clear();
		if (mCandidatesView != null) mCandidatesView.hide();
		if (mCandidatesFrame != null) mCandidatesFrame.setVisibility(View.GONE);
		super.onFinishInputView(finishingInput);
	}

	@Override
	public void onFinishCandidatesView(boolean finishingInput) {
		super.onFinishCandidatesView(finishingInput);
	}

	@Override
	public boolean onEvaluateInputViewShown() {
		if (hideWindowByKey) {
			hideWindowByKey = false;
			return false;
		}
		EditorInfo editorInfo = getCurrentInputEditorInfo();
		if (editorInfo == null || editorInfo.inputType == EditorInfo.TYPE_NULL) {
			return false;
		}
		return true;
	}

	@Override
	public boolean onEvaluateFullscreenMode() {
		return false;
	}

	private boolean isSendToAdbService(Object data){
		if(AdbHelper.initService(getApplicationContext())){
			AdbHelper.getInstance().sendData(data);
			return true;
		}
		return false;
	}

	private void startRemoteServer(){
		do {
			mServer = new RemoteServer(RemoteServer.serverPort, this);
			mServer.setDataReceiver(new RemoteServer.DataReceiver() {
				@Override
				public void onKeyEventReceived(String keyCode, final int keyAction) {
					if(keyCode != null) {
						if("cls".equalsIgnoreCase(keyCode)){
							InputConnection ic = getCurrentInputConnection();
							if(ic != null) {
								ic.deleteSurroundingText(Integer.MAX_VALUE,Integer.MAX_VALUE);
							}
						}else {
							final int kc = KeyEvent.keyCodeFromString(keyCode);
							if(kc != KeyEvent.KEYCODE_UNKNOWN){
								if(mInputView != null && KeyEventUtils.isKeyboardFocusEvent(kc) && mInputView.isShown()){
									if(keyAction == KEY_ACTION_PRESSED || keyAction == KEY_ACTION_DOWN) {
										handler.post(new Runnable() {
											@Override
											public void run() {
												if (!handleKeyboardFocusEvent(kc)) {
													if(!isSendToAdbService(kc)) sendKeyCode(kc);
												}
											}
										});
									}
								}
								else{
									long eventTime = SystemClock.uptimeMillis();
									InputConnection ic = getCurrentInputConnection();
									switch (keyAction) {
										case KEY_ACTION_PRESSED:
											if(!isSendToAdbService(kc)) sendKeyCode(kc);
											break;
										case KEY_ACTION_DOWN:
											if(!isSendToAdbService(kc) && ic != null) {
												ic.sendKeyEvent(new KeyEvent(eventTime, eventTime,
														KeyEvent.ACTION_DOWN, kc, 0, 0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
														KeyEvent.FLAG_SOFT_KEYBOARD | KeyEvent.FLAG_KEEP_TOUCH_MODE));
											}
											break;
										case KEY_ACTION_UP:
											if(ic != null) {
												ic.sendKeyEvent(new KeyEvent(eventTime, eventTime,
													KeyEvent.ACTION_UP, kc, 0, 0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
													KeyEvent.FLAG_SOFT_KEYBOARD | KeyEvent.FLAG_KEEP_TOUCH_MODE));
											}
											break;
									}
								}
							}
						}
					}
				}

				@Override
				public void onTextReceived(String text) {
					if (text != null) {
						if(!isSendToAdbService(text))commitText(text);
					}
				}
			});
			try {
				mServer.start();
				Environment.toastInHandler(this, getString(R.string.app_name)  + "远程服务已启动");
				Log.i(TAG, "远程服务创建成功！port=" + RemoteServer.serverPort);
				break;
			}catch (IOException ex){
				Log.e(TAG, "建立输入HTTP服务时出错", ex);
				RemoteServer.serverPort ++;
				mServer.stop();
			}
		}while (RemoteServer.serverPort < 9999);
	}

	private boolean commitText(String text){
		InputConnection ic = getCurrentInputConnection();
		boolean flag = false;
		if (ic != null){
			if(text.length() > 1 && ic.beginBatchEdit()){
				flag = ic.commitText(text, 1);
				ic.endBatchEdit();
			}else{
				flag = ic.commitText(text, 1);
			}
		}
		return flag;
	}
	private void sendKeyCode(int keyCode){
		if(keyCode == KeyEvent.KEYCODE_HOME){
			Intent i = new Intent(Intent.ACTION_MAIN);
			i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			i.addCategory(Intent.CATEGORY_HOME);
			this.startActivity(i);
		}else {
			sendDownUpKeyEvents(keyCode);
		}
	}
    
    public void onDestroy() {
		if (mServer != null && mServer.isStarting()){
            Log.i(TAG, "远程输入服务已停止！");
			mServer.stop();
		}
		DLNAUtils.stopDLNAService();
		AdbHelper.stopService();
		Environment.toastInHandler(this, getString(R.string.app_name)  + "服务已停止");
    	super.onDestroy();    	
    }

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if(handleKeyboardFocusEvent(keyCode)) return true;
		if(keyCode == KeyEvent.KEYCODE_CAPS_LOCK) capsOn = !capsOn;

		if (mChineseMode && mCandidatesView.isShown()) {
			if (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9) {
				int num = keyCode - KeyEvent.KEYCODE_0;
				if (num == 0) {
					mPinyinEngine.nextPage();
				} else {
					int globalIdx = mPinyinEngine.selectCandidateByNumber(num);
					if (globalIdx >= 0) {
						commitAndReturnFocus(mPinyinEngine.getSelectedCandidate(globalIdx));
					}
				}
				return true;
			}
			if (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
				String c = mPinyinEngine.getHighlightedCandidate();
				if (c != null) {
					commitAndReturnFocus(c);
				}
				return true;
			}
			if (keyCode == KeyEvent.KEYCODE_SPACE && mPinyinEngine.getCandidateCount() > 0) {
				String c = mPinyinEngine.getHighlightedCandidate();
				if (c != null) {
					commitAndReturnFocus(c);
				}
				return true;
			}
			if (keyCode == KeyEvent.KEYCODE_DEL) {
				mPinyinEngine.processBackspace();
				return true;
			}
		}

		if ((keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9)) {
			if(commitText(String.valueOf(keyCode - KeyEvent.KEYCODE_0))) return true;
		} else if (keyCode >= KeyEvent.KEYCODE_A && keyCode <= KeyEvent.KEYCODE_Z) {
			if (commitText(String.valueOf((char) ((capsOn ? 65 : 97) + keyCode - KeyEvent.KEYCODE_A))))
				return true;
		}
		return super.onKeyDown(keyCode, event);
	}

	private boolean handleKeyboardFocusEvent(int keyCode){
		if(mInputView != null) {
			switch (keyCode) {
				case KeyEvent.KEYCODE_DPAD_UP:
					// Prevent focus from escaping keyboard area
					if (focusedView != null && focusedView.getId() == R.id.qrThumbContainer) {
						return true;
					}
					if (mCandidatesFrame != null && mCandidatesFrame.getVisibility() == View.VISIBLE
							&& focusedView != null) {
						LinearLayout firstRow = findFirstKeyRow();
						if (firstRow != null && focusedView.getParent() == firstRow) {
							focusedView = findFirstFocusableIn(mCandidatesFrame);
							if (focusedView != null) {
								focusedView.requestFocus();
								focusedView.requestFocusFromTouch();
							}
							return true;
						}
					}
					if (isInCandidateBar(focusedView)) {
						return true;
					}
					if(mInputView.isShown()) {
						requestNextButtonFocus(keyCode);
						return true;
					}
					break;
				case KeyEvent.KEYCODE_DPAD_DOWN:
					// If focus on QR, move to first key row
					if (focusedView != null && focusedView.getId() == R.id.qrThumbContainer) {
						LinearLayout firstRow = findFirstKeyRow();
						if (firstRow != null) {
							focusedView = findFocusableChildAt(firstRow, 0, true);
							if (focusedView != null) {
								focusedView.requestFocus();
								focusedView.requestFocusFromTouch();
							}
						}
						return true;
					}
					if (isInCandidateBar(focusedView)) {
						LinearLayout firstRow = findFirstKeyRow();
						if (firstRow != null && firstRow.getChildCount() > 0) {
							focusedView = firstRow.getChildAt(0);
							focusedView.requestFocus();
							focusedView.requestFocusFromTouch();
						}
						return true;
					}
					if(mInputView.isShown()) {
						requestNextButtonFocus(keyCode);
						return true;
					}
					break;
				case KeyEvent.KEYCODE_DPAD_LEFT:
					if (isInCandidateBar(focusedView)) {
						ViewGroup parent = (ViewGroup) focusedView.getParent();
						int idx = parent.indexOfChild(focusedView);
						if (idx > 0) {
							focusedView = parent.getChildAt(idx - 1);
							focusedView.requestFocus();
							focusedView.requestFocusFromTouch();
						} else {
							mPinyinEngine.prevPage();
							mCandidatesFrame.post(new Runnable() {
								public void run() {
									View first = findFirstFocusableIn(mCandidatesFrame);
									if (first != null) {
										focusedView = first;
										first.requestFocus();
										first.requestFocusFromTouch();
									}
								}
							});
						}
						return true;
					}
					// If on first key of any row and QR is focusable, focus QR
					if (focusedView != null && focusedView.getParent() instanceof LinearLayout) {
						LinearLayout row = (LinearLayout) focusedView.getParent();
						int rowIdx = mInputView.indexOfChild(row);
						if (rowIdx >= 0 && row.getId() != R.id.helpDialog
								&& row.getId() != R.id.qrArea && row.getId() != R.id.candidatesFrame) {
							int prevIdx = findPrevFocusableIndex(row, row.indexOfChild(focusedView));
							if (prevIdx < 0) {
								View qr = mInputView.findViewById(R.id.qrThumbContainer);
								if (qr != null && qr.isFocusable() && qr.getVisibility() == View.VISIBLE) {
									focusedView = qr;
									focusedView.requestFocus();
									focusedView.requestFocusFromTouch();
									return true;
								}
							}
						}
					}
					if(mInputView.isShown()) {
						requestNextButtonFocus(keyCode);
						return true;
					}
					break;
				case KeyEvent.KEYCODE_DPAD_RIGHT:
					if (isInCandidateBar(focusedView)) {
						ViewGroup parent = (ViewGroup) focusedView.getParent();
						int idx = parent.indexOfChild(focusedView);
						if (idx < parent.getChildCount() - 1) {
							focusedView = parent.getChildAt(idx + 1);
							focusedView.requestFocus();
							focusedView.requestFocusFromTouch();
						} else {
							mPinyinEngine.nextPage();
							mCandidatesFrame.post(new Runnable() {
								public void run() {
									View first = findFirstFocusableIn(mCandidatesFrame);
									if (first != null) {
										focusedView = first;
										first.requestFocus();
										first.requestFocusFromTouch();
									}
								}
							});
						}
						return true;
					}
					// If focus is on QR thumbnail, move to first key of first row
					if (focusedView != null && focusedView.getId() == R.id.qrThumbContainer) {
						LinearLayout firstRow = findFirstKeyRow();
						if (firstRow != null) {
							focusedView = findFocusableChildAt(firstRow, 0, true);
							if (focusedView != null) {
								focusedView.requestFocus();
								focusedView.requestFocusFromTouch();
							}
						}
						return true;
					}
					if(mInputView.isShown()) {
						requestNextButtonFocus(keyCode);
						return true;
					}
					break;
				case KeyEvent.KEYCODE_ENTER:
				case KeyEvent.KEYCODE_DPAD_CENTER:
					if (isInCandidateBar(focusedView) && focusedView != null) {
						focusedView.performClick();
						return true;
					}
					// QR thumbnail click
					if (focusedView != null && focusedView.getId() == R.id.qrThumbContainer) {
						focusedView.performClick();
						return true;
					}
					if (mInputView.isShown() && focusedView != null) {
						clickButtonByKey(focusedView);
						return true;
					}
					break;
				case KeyEvent.KEYCODE_CAPS_LOCK:
					toggleCapsState(true);
					return true;
				case KeyEvent.KEYCODE_ESCAPE:
				case KeyEvent.KEYCODE_BACK:
					if (mInputView.isShown()){
						if(helpDialog != null && helpDialog.isShown()){
							helpDialog.setVisibility(View.GONE);
						}else {
							this.finishInput();
						}
						return true;
					}
					break;
			}
		}
		return false;
	}

	private LinearLayout findFirstKeyRow() {
		for (int i = 0; i < mInputView.getChildCount(); i++) {
			View child = mInputView.getChildAt(i);
			if (child instanceof LinearLayout && child.getVisibility() == View.VISIBLE
				&& child.getId() != R.id.helpDialog
				&& child.getId() != R.id.qrArea
				&& child.getId() != R.id.candidatesFrame) {
				return (LinearLayout) child;
			}
		}
		return null;
	}

	private boolean isInCandidateBar(View v) {
		if (v == null || mCandidatesFrame == null) return false;
		ViewParent p = v.getParent();
		while (p != null) {
			if (p == mCandidatesFrame || p == mCandidatesView.getView()) return true;
			if (p instanceof View) {
				p = ((View) p).getParent();
			} else {
				break;
			}
		}
		return false;
	}

	private View findFirstFocusableIn(ViewGroup parent) {
		for (int i = 0; i < parent.getChildCount(); i++) {
			View child = parent.getChildAt(i);
			if (child.isFocusable()) return child;
			if (child instanceof ViewGroup) {
				View found = findFirstFocusableIn((ViewGroup) child);
				if (found != null) return found;
			}
		}
		return null;
	}

	private int findPrevKeyRowIndex(int fromIndex) {
		for (int i = fromIndex - 1; i >= 0; i--) {
			View c = mInputView.getChildAt(i);
			if (c instanceof LinearLayout && c.getVisibility() == View.VISIBLE
				&& c.getId() != R.id.helpDialog && c.getId() != R.id.qrArea
				&& c.getId() != R.id.candidatesFrame) {
				return i;
			}
		}
		for (int i = mInputView.getChildCount() - 1; i > fromIndex; i--) {
			View c = mInputView.getChildAt(i);
			if (c instanceof LinearLayout && c.getVisibility() == View.VISIBLE
				&& c.getId() != R.id.helpDialog && c.getId() != R.id.qrArea
				&& c.getId() != R.id.candidatesFrame) {
				return i;
			}
		}
		return -1;
	}

	private int findNextKeyRowIndex(int fromIndex) {
		for (int i = fromIndex + 1; i < mInputView.getChildCount(); i++) {
			View c = mInputView.getChildAt(i);
			if (c instanceof LinearLayout && c.getVisibility() == View.VISIBLE
				&& c.getId() != R.id.helpDialog && c.getId() != R.id.qrArea
				&& c.getId() != R.id.candidatesFrame) {
				return i;
			}
		}
		for (int i = 0; i < fromIndex; i++) {
			View c = mInputView.getChildAt(i);
			if (c instanceof LinearLayout && c.getVisibility() == View.VISIBLE
				&& c.getId() != R.id.helpDialog && c.getId() != R.id.qrArea
				&& c.getId() != R.id.candidatesFrame) {
				return i;
			}
		}
		return -1;
	}

	private void requestNextButtonFocus(int keyCode){
		if(focusedView == null){
			LinearLayout firstRow = findFirstKeyRow();
			if (firstRow != null && firstRow.getChildCount() > 0) {
				for (int i = 0; i < firstRow.getChildCount(); i++) {
					if (firstRow.getChildAt(i).isFocusable()) {
						focusedView = firstRow.getChildAt(i);
						break;
					}
				}
			}
		}else {
			ViewParent parent = focusedView.getParent();
			if (!(parent instanceof LinearLayout)) {
				return;
			}
			LinearLayout container = (LinearLayout)parent;
			int rootInde = mInputView.indexOfChild(container);
			if (rootInde < 0) return;
			int index = container.indexOfChild(focusedView);
			switch (keyCode) {
				case KeyEvent.KEYCODE_DPAD_UP: {
					int prevIdx = findPrevKeyRowIndex(rootInde);
					if (prevIdx >= 0) {
						container = (LinearLayout) mInputView.getChildAt(prevIdx);
						index = Math.min(index, container.getChildCount() - 1);
						focusedView = findFocusableChildAt(container, index, true);
					}
					break;
				}
				case KeyEvent.KEYCODE_DPAD_DOWN: {
					int nextIdx = findNextKeyRowIndex(rootInde);
					if (nextIdx >= 0) {
						container = (LinearLayout) mInputView.getChildAt(nextIdx);
						index = Math.min(index, container.getChildCount() - 1);
						focusedView = findFocusableChildAt(container, index, true);
					}
					break;
				}
				case KeyEvent.KEYCODE_DPAD_LEFT:
					index = findPrevFocusableIndex(container, index);
					if (index < 0) {
						int prevIdx = findPrevKeyRowIndex(rootInde);
						if (prevIdx >= 0) {
							container = (LinearLayout) mInputView.getChildAt(prevIdx);
							focusedView = findFocusableChildAt(container, container.getChildCount() - 1, false);
							break;
						}
					}
					if (index >= 0) {
						focusedView = container.getChildAt(index);
					}
					break;
				case KeyEvent.KEYCODE_DPAD_RIGHT:
					index = findNextFocusableIndex(container, index);
					if (index < 0) {
						int nextIdx = findNextKeyRowIndex(rootInde);
						if (nextIdx >= 0) {
							container = (LinearLayout) mInputView.getChildAt(nextIdx);
							focusedView = findFocusableChildAt(container, 0, true);
							break;
						}
					}
					if (index >= 0 && index < container.getChildCount()) {
						focusedView = container.getChildAt(index);
					}
					break;
			}
		}

		if (focusedView != null) {
			focusedView.requestFocus();
			focusedView.requestFocusFromTouch();
		}
	}

	private int findPrevFocusableIndex(LinearLayout container, int startIdx) {
		for (int i = startIdx - 1; i >= 0; i--) {
			if (container.getChildAt(i).isFocusable()) return i;
		}
		return -1;
	}

	private int findNextFocusableIndex(LinearLayout container, int startIdx) {
		for (int i = startIdx + 1; i < container.getChildCount(); i++) {
			if (container.getChildAt(i).isFocusable()) return i;
		}
		return -1;
	}

	private View findFocusableChildAt(LinearLayout container, int startIdx, boolean forward) {
		if (forward) {
			for (int i = startIdx; i < container.getChildCount(); i++) {
				if (container.getChildAt(i).isFocusable()) return container.getChildAt(i);
			}
			for (int i = startIdx - 1; i >= 0; i--) {
				if (container.getChildAt(i).isFocusable()) return container.getChildAt(i);
			}
		} else {
			for (int i = startIdx; i >= 0; i--) {
				if (container.getChildAt(i).isFocusable()) return container.getChildAt(i);
			}
			for (int i = container.getChildCount() - 1; i > startIdx; i--) {
				if (container.getChildAt(i).isFocusable()) return container.getChildAt(i);
			}
		}
		return null;
	}

	private void finishInput(){
		this.requestHideSelf(0);
	}

	private void clickButtonByKey(final View v){
		int id = v.getId();
		if (id == R.id.btnCaps) {
			v.setBackgroundResource(R.drawable.kb_key_normal);
		} else {
			v.setBackgroundResource(v instanceof ImageButton ? R.drawable.kb_key_alt : R.drawable.kb_key_normal);
		}
		clickButton(v, false);
		handler.postDelayed(new Runnable() {
			@Override
			public void run() {
				if(v == btnCaps){
					v.setBackgroundResource(R.drawable.kb_key_normal);
				}else{
					v.setBackgroundResource(v instanceof ImageButton ? R.drawable.kb_key_alt : R.drawable.kb_key_normal);
				}
				v.requestFocus();
			}
		}, 200);
	}

	private boolean isLetterButton(Button btn) {
		String text = btn.getText().toString();
		return text.length() == 1 && Character.isLetter(text.charAt(0));
	}

	private boolean isNumberButton(Button btn) {
		String text = btn.getText().toString();
		return text.length() == 1 && Character.isDigit(text.charAt(0));
	}

	private void commitAndReturnFocus(String text) {
		commitText(text);
		mPinyinEngine.clear();
		mCandidatesView.hide();
		mCandidatesFrame.setVisibility(View.GONE);
		LinearLayout firstRow = findFirstKeyRow();
		if (firstRow != null) {
			focusedView = findFocusableChildAt(firstRow, 0, true);
			if (focusedView != null) {
				focusedView.requestFocus();
				focusedView.requestFocusFromTouch();
			}
		}
	}

	private void selectChineseCandidate(int index) {
		String candidate = mPinyinEngine.getHighlightedCandidate();
		if (candidate != null) {
			commitText(candidate);
			mPinyinEngine.clear();
			mCandidatesView.hide();
			mCandidatesFrame.setVisibility(View.GONE);
			LinearLayout firstRow = findFirstKeyRow();
			if (firstRow != null) {
				focusedView = findFocusableChildAt(firstRow, 0, true);
				if (focusedView != null) {
					focusedView.requestFocus();
					focusedView.requestFocusFromTouch();
				}
			}
		}
	}

	private void clickButton(View v, boolean resetCapsButtonState){
		if(v instanceof Button){
			String text = ((Button) v).getText().toString();
			int id = v.getId();

			if (id == R.id.btnToggleChinese) {
				toggleChineseMode();
				return;
			}
			if (id == R.id.btnToggleSymbol) {
				toggleSymbolPage();
				return;
			}
			if (id == R.id.btnCaps) {
				toggleCapsState(resetCapsButtonState);
				return;
			}
			if (id == R.id.btnSpace) {
				if (mChineseMode && !mSymbolPage && mPinyinEngine.hasInput()
						&& mPinyinEngine.getCandidateCount() > 0) {
					selectChineseCandidate(0);
				} else {
					sendKeyCode(KeyEvent.KEYCODE_SPACE);
				}
				return;
			}
			if (id == R.id.btnLeft) {
				sendKeyCode(KeyEvent.KEYCODE_DPAD_LEFT);
				return;
			}
			if (id == R.id.btnRight) {
				sendKeyCode(KeyEvent.KEYCODE_DPAD_RIGHT);
				return;
			}

			// Chinese mode: letter keys accumulate pinyin
			if (mChineseMode && !mSymbolPage && isLetterButton((Button) v)) {
				mPinyinEngine.processLetter(text.charAt(0));
				return;
			}

			// Chinese mode: number keys select candidates or flip page
			if (mChineseMode && mCandidatesView.isShown() && isNumberButton((Button) v)) {
				int num = Integer.parseInt(text);
				if (num == 0) {
					mPinyinEngine.nextPage();
				} else {
					int globalIdx = mPinyinEngine.selectCandidateByNumber(num);
					if (globalIdx >= 0) {
						commitText(mPinyinEngine.getSelectedCandidate(globalIdx));
						mPinyinEngine.clear();
						mCandidatesView.hide();
						mCandidatesFrame.setVisibility(View.GONE);
					}
				}
				return;
			}

			// Handle special text buttons
			if (id == R.id.btnDelete) {
				if (mChineseMode && mPinyinEngine.hasInput()) {
					mPinyinEngine.processBackspace();
				} else {
					sendKeyCode(KeyEvent.KEYCODE_DEL);
				}
				return;
			}
			if (id == R.id.btnEnter) {
				if (mChineseMode && !mSymbolPage && mPinyinEngine.hasInput()) {
					selectChineseCandidate(0);
				} else {
					sendKeyCode(KeyEvent.KEYCODE_ENTER);
				}
				return;
			}

			commitText(text);
		}
	}

	@Override
	public void onClick(View v) {
		clickButton(v, true);
		v.requestFocusFromTouch();
		focusedView = v;
	}

	private void toggleSymbolPage() {
		mSymbolPage = !mSymbolPage;
		if (mSymbolPage) {
			if (mChineseMode) {
				mChineseMode = false;
				mBtnToggleChinese.setText("中");
				mPinyinEngine.clear();
				mCandidatesView.hide();
				mCandidatesFrame.setVisibility(View.GONE);
			}
			mBtnToggleSymbol.setText("ABC");
			applySymbolPage();
		} else {
			mBtnToggleSymbol.setText("符");
			applyAbcPage();
		}
	}

	private void applySymbolPage() {
		setButtonTextsFromIndex(row1, 3, SYM_ROW1);
		setButtonTextsFromIndex(row2, 3, SYM_ROW2);
		setButtonTextsFromIndex(row3, 3, SYM_ROW3);
		setButtonTextsFromIndex(row4, 3, SYM_ROW4);
	}

	private void applyAbcPage() {
		setButtonTextsFromIndex(row1, 3, ABC_ROW1);
		setButtonTextsFromIndex(row2, 3, ABC_ROW2);
		setButtonTextsFromIndex(row3, 3, ABC_ROW3);
		setButtonTextsFromIndex(row4, 3, ABC_ROW4);
		if (capsOn) {
			resetButtonCharCase(row1, true);
			resetButtonCharCase(row2, true);
			resetButtonCharCase(row3, true);
			resetButtonCharCase(row4, true);
		}
	}

	private void setButtonTextsFromIndex(LinearLayout layout, int startIdx, String[] texts) {
		int textIdx = 0;
		for (int i = 0; i < layout.getChildCount(); i++) {
			View v = layout.getChildAt(i);
			if (v instanceof Button && i >= startIdx && textIdx < texts.length) {
				((Button) v).setText(texts[textIdx]);
				textIdx++;
			}
		}
	}

	private void toggleChineseMode() {
		mChineseMode = !mChineseMode;
		if (mChineseMode) {
			if (mSymbolPage) {
				toggleSymbolPage();
			}
			mBtnToggleChinese.setText("EN");
		} else {
			mBtnToggleChinese.setText("中");
			mPinyinEngine.clear();
			mCandidatesView.hide();
			mCandidatesFrame.setVisibility(View.GONE);
		}
	}

	private void toggleCapsState(boolean resetCapsButtonState){
		capsOn = !capsOn;
		resetButtonChar(row1);
		resetButtonChar(row2);
		resetButtonChar(row3);
		resetButtonChar(row4);
	}

	private void resetButtonChar(LinearLayout layout){
		for(int i =0; i<layout.getChildCount(); i++){
			View v = layout.getChildAt(i);
			if(v instanceof Button){
				Button b = (Button)v;
				String text = b.getText().toString();
				if (text.length() == 1 && Character.isLetter(text.charAt(0))) {
					b.setText(capsOn ? text.toUpperCase() : text.toLowerCase());
				}
			}
		}
	}

	private void resetButtonCharCase(LinearLayout layout, boolean toUpper){
		for(int i =0; i<layout.getChildCount(); i++){
			View v = layout.getChildAt(i);
			if(v instanceof Button){
				Button b = (Button)v;
				String text = b.getText().toString();
				if (text.length() == 1 && Character.isLetter(text.charAt(0))) {
					b.setText(toUpper ? text.toUpperCase() : text.toLowerCase());
				}
			}
		}
	}

	private void showHelpDialog(){
		if(mServer == null) return;

        if(addressView.getText().length() == 0) {
            String version = AppPackagesHelper.getCurrentPackageVersion(this);
            String address = mServer.getServerAddress();
            TextView title = helpDialog.findViewById(R.id.title);
            title.setText("远程输入，请扫描或直接访问以下地址：" + address);
            addressView.setText(address);
            qrCodeImage.setImageBitmap(QRCodeGen.generateBitmap(address, 240, 240));
        }

		// Refresh QR thumbnail
		ImageView ivQrThumb = mInputView.findViewById(R.id.ivQrThumb);
		if (ivQrThumb != null && mServer.getServerAddress() != null) {
			ivQrThumb.setImageBitmap(QRCodeGen.generateBitmap(mServer.getServerAddress(), 160, 160));
		}

		helpDialog.setVisibility(View.VISIBLE);
	}

}
