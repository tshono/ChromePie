/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.jt5.xposed.chromepie;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.app.Activity;
import android.content.res.XModuleResources;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.TextView;

import com.jt5.xposed.chromepie.view.PieItem;
import com.jt5.xposed.chromepie.view.PieMenu;

import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;

/**
 * Controller for Quick Controls pie menu
 */
public class PieControl implements PieMenu.PieController, OnClickListener {

    private final Activity mChromeActivity;
    private final XModuleResources mXResources;
    private PieMenu mPie;
    private final Controller mController;
    private final Map<String,Action> mActionMap = new HashMap<String,Action>();
    private final int mItemSize;
    private final XSharedPreferences mXPreferences;
    private static final String PACKAGE_NAME = PieControl.class.getPackage().getName();
    private static final String TAG = "ChromePie:PieControl: ";

    PieControl(Object mainObj, XModuleResources mModRes, ClassLoader classLoader) {
        mChromeActivity = (Activity) mainObj;
        mController = new Controller(this, mChromeActivity, classLoader);
        mXResources = mModRes;
        mXPreferences = new XSharedPreferences(PACKAGE_NAME);
        mXPreferences.makeWorldReadable();
        mItemSize = (int) mXResources.getDimension(R.dimen.qc_item_size);
    }

    protected void attachToContainer(FrameLayout container) {
        if (mPie == null) {
            mPie = new PieMenu(mChromeActivity, mXResources, mController);
            LayoutParams lp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
            mPie.setLayoutParams(lp);
            populateMenu();
            mPie.setController(this);
        }
        container.addView(mPie);
    }

    protected void removeFromContainer(FrameLayout container) {
        container.removeView(mPie);
    }

    protected void forceToTop(FrameLayout container) {
        if (mPie.getParent() != null) {
            container.removeView(mPie);
            container.addView(mPie);
        }
    }

    String getTriggerSide() {
        return mXPreferences.getString("trigger_side", "both");
    }

    @Override
    public boolean onOpen() {
        View icon;
        List<PieItem> items = mPie.getItems();
        for (PieItem item : items) {
            icon = item.getView();
            if (icon == null) {
                continue;
            }
            if (!(icon instanceof ImageView)) {
                //icon = icon.findViewById(R.id.count_label);
                icon = ((ViewGroup) icon).getChildAt(1);
            }
            if (item.getId().equals("forward")) {
                item.setEnabled(mController.canGoForward());
            } else if (item.getId().equals("back")) {
                item.setEnabled(mController.canGoBack());
            } else if (item.getId().equals("desktop_site")) {
                if (mController.isDesktopUserAgent()) {
                    ((ImageView) icon).setImageDrawable(mXResources.getDrawable(R.drawable.ic_mobile));
                } else {
                    ((ImageView) icon).setImageDrawable(mXResources.getDrawable(R.drawable.ic_desktop_holo_dark));
                }
            } else if (item.getId().equals("refresh")) {
                if (mController.isLoading()) {
                    ((ImageView) icon).setImageDrawable(mXResources.getDrawable(R.drawable.ic_action_cancel));
                } else {
                    ((ImageView) icon).setImageDrawable(mXResources.getDrawable(R.drawable.ic_action_refresh));
                }
            } else if (item.getId().equals("fullscreen")) {
                if (mController.isFullscreen()) {
                    ((ImageView) icon).setImageDrawable(mXResources.getDrawable(R.drawable.ic_action_return_from_full_screen));
                } else {
                    ((ImageView) icon).setImageDrawable(mXResources.getDrawable(R.drawable.ic_action_full_screen));
                }
            } else if (item.getId().equals("show_tabs")) {
                ((TextView) icon).setText(Integer.toString(mController.getTabCount()));
            } else if (item.getId().equals("find_in_page")) {
                item.setEnabled(mController.tabSupportsFinding());
            } else if (item.getId().equals("print")) {
                item.setEnabled(mController.printingSupported());
            } else if (item.getId().equals("recent_tabs")) {
                item.setEnabled(mController.syncSupported());
            } else if (item.getId().equals("add_bookmark")) {
                if (mController.bookmarkExists()) {
                    ((ImageView) icon).setImageDrawable(mXResources.getDrawable(R.drawable.ic_action_not_important));
                } else {
                    ((ImageView) icon).setImageDrawable(mXResources.getDrawable(R.drawable.ic_action_important));
                }
                item.setEnabled(mController.editBookmarksSupported());
            }
            if (item.getId().equals("add_to_home") || item.getId().equals("recent_tabs") || item.getId().equals("most_visited")) {
                item.setEnabled(!mController.isIncognito());
            }
            if (item.getId().equals("add_to_home") || item.getId().equals("share")) {
                String str = mController.getUrl();
                item.setEnabled(!(str.startsWith("chrome://") || str.startsWith("chrome-native://")));
            }
        }
        return true;
    }

    private void populateMenu() {
        String[] actions = mXResources.getStringArray(R.array.pie_item_actions);
        String[] values = mXResources.getStringArray(R.array.pie_item_values);
        String[] drawables = mXResources.getStringArray(R.array.pie_item_dark_drawables);
        mPie.clearItems();
        mXPreferences.reload();
        Map<String, ?> keys = mXPreferences.getAll();
        mActionMap.put("Action_main", new Action_main());
        for (int i = 1; i < 7; i++) {
            if (mXPreferences.getBoolean("screen_slice_" + i, false)) {
                String key = "slice_" + i + "_item_" + i;
                if (keys.containsKey(key)) {
                    String value = (String) keys.get(key);
                    int pos = Arrays.asList(values).indexOf(value);
                    PieItem item = makeItem(value, actions[pos], drawables[pos], 1);
                    mPie.addItem(item);
                    addAction(actions[pos], value);
                    for (int j = 1; j < 7; j++) {
                        if (i == j) {
                            continue;
                        }
                        key = "slice_" + i + "_item_" + j;
                        if (keys.containsKey(key)) {
                            value = (String) keys.get(key);
                            pos = Arrays.asList(values).indexOf(value);
                            item.addItem(makeItem(value, actions[pos], drawables[pos], 1));
                            addAction(actions[pos], value);
                        } else {
                            item.addItem(makeFiller());
                        }
                    }
                } else {
                    mPie.addItem(makeFiller());
                }
            }
        }
    }

    private void addAction(String action, String id) {
        if (id.equals("none")) {
            return;
        }
        try {
            if (action.isEmpty()) {
                mActionMap.put("Action_" + id, (Action) Class.forName(PACKAGE_NAME + ".Action_" + id).newInstance());
            }
        } catch (InstantiationException ie) {
            XposedBridge.log(TAG + ie);
        } catch (IllegalAccessException iae) {
            XposedBridge.log(TAG + iae);
        } catch (ClassNotFoundException cnfe) {
            XposedBridge.log(TAG + cnfe);
        }
    }

    @Override
    public void onClick(View v) {
        ItemInfo info = (ItemInfo) v.getTag();
        if (mActionMap.containsKey("Action_" + info.id)) {
            mActionMap.get("Action_" + info.id).execute(mController);
        } else {
            if (info.action != null) {
                ((Action_main) mActionMap.get("Action_main")).executeMain(mController, info.action);
            }
        }
    }

    private PieItem makeItem(String id, String action, String icon, int level) {
        if (id.equals("none")) {
            return makeFiller();
        }
        ItemInfo info = new ItemInfo();
        info.action = action;
        info.id = id;
        if (id.equals("show_tabs")) {
            View tabs = makeTabsView();
            tabs.setTag(info);
            PieItem tabsItem = new PieItem(tabs, id, "", level);
            tabs.setOnClickListener(this);
            return tabsItem;
        }
        ImageView view = new ImageView(mChromeActivity);
        view.setImageDrawable(mXResources.getDrawable(mXResources.getIdentifier(icon, "drawable", PACKAGE_NAME)));
        view.setMinimumWidth(mItemSize);
        view.setMinimumHeight(mItemSize);
        view.setTag(info);
        view.setScaleType(ScaleType.CENTER);
        LayoutParams lp = new LayoutParams(mItemSize, mItemSize);
        view.setLayoutParams(lp);
        view.setOnClickListener(this);
        PieItem item = new PieItem(view, id, action, level);
        return item;
    }

    private PieItem makeFiller() {
        return new PieItem(null, null, null, 1);
    }

    @SuppressWarnings("deprecation")
    private View makeTabsView() {
        LayoutInflater li = mChromeActivity.getLayoutInflater();
        View view = li.inflate(mXResources.getLayout(com.jt5.xposed.chromepie.R.layout.qc_tabs_view), null);
        // findViewById returns null on some versions of Chrome
        // (some sort of resource conflict?) - so use getChildAt
        //TextView count = (TextView) view.findViewById(R.id.count_label);
        TextView count = (TextView) ((ViewGroup) view).getChildAt(1);
        count.setBackgroundDrawable(mXResources.getDrawable(R.drawable.tab_nr));
        count.setText(Integer.toString(mController.getTabCount()));
        //ImageView icon = (ImageView) view.findViewById(R.id.count_icon);
        ImageView icon = (ImageView) ((ViewGroup) view).getChildAt(0);
        icon.setImageDrawable(mXResources.getDrawable(R.drawable.ic_windows_holo_dark));
        icon.setScaleType(ScaleType.CENTER);
        LayoutParams lp = new LayoutParams(mItemSize, mItemSize);
        view.setLayoutParams(lp);
        return view;
    }

    static class ItemInfo {
        private String id;
        private String action;
    }

}
