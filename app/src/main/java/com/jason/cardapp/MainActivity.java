package com.jason.cardapp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class MainActivity extends Activity {
    private static final String PREF_NAME = "card_app_native_db";
    private static final String PREF_KEY = "db_json";
    private static final int REQ_PICK_IMAGE = 1001;

    private LinearLayout root;
    private final ArrayList<CardData> cards = new ArrayList<>();
    private final ArrayList<DeckData> decks = new ArrayList<>();

    private int selectedDeckIndex = 0;
    private String draftCardName = "";
    private String draftCardCost = "";
    private String draftCardDesc = "";
    private String draftCardImageUri = "";
    private EditText cardNameInput;
    private EditText cardCostInput;
    private EditText cardDescInput;

    private DeckData activeDeck;
    private final ArrayList<CardData> drawPile = new ArrayList<>();
    private final ArrayList<CardData> hand = new ArrayList<>();
    private final ArrayList<CardData> discardPile = new ArrayList<>();
    private final ArrayList<CardData> energyZone = new ArrayList<>();
    private boolean handFaceUp = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        loadData();
        showMainMenu();
    }

    private void showMainMenu() {
        setBaseScreen("卡牌牌組工具", false, null);
        TextView subtitle = text("原生 Android 單機版，不使用 WebView，不需要把 HTML 轉 APK。", 15, false);
        subtitle.setTextColor(Color.rgb(80, 80, 80));
        subtitle.setPadding(dp(2), 0, dp(2), dp(18));
        root.addView(subtitle);

        root.addView(bigButton("製作卡牌", v -> showCardManager()));
        root.addView(bigButton("編輯牌組", v -> showDeckManager()));
        root.addView(bigButton("使用牌組", v -> showUseDeckSelector()));

        TextView hint = text("資料會儲存在手機本機 App 資料中。刪除 App 或清除資料時會一起刪除。", 13, false);
        hint.setTextColor(Color.rgb(110, 110, 110));
        hint.setPadding(0, dp(20), 0, 0);
        root.addView(hint);
    }

    private void showCardManager() {
        setBaseScreen("製作卡牌", true, this::showMainMenu);

        TextView section = sectionTitle("新增卡牌");
        root.addView(section);

        cardNameInput = input("卡牌名稱", draftCardName);
        cardCostInput = input("費用", draftCardCost);
        cardDescInput = input("描述", draftCardDesc);
        cardDescInput.setMinLines(3);
        cardDescInput.setGravity(Gravity.TOP | Gravity.START);

        root.addView(label("名稱"));
        root.addView(cardNameInput);
        root.addView(label("費用"));
        root.addView(cardCostInput);
        root.addView(label("描述"));
        root.addView(cardDescInput);

        LinearLayout imageRow = row();
        imageRow.addView(button("選擇圖片", v -> pickCardImage()), weightParams(1));
        TextView imageStatus = text(draftCardImageUri.isEmpty() ? "尚未選擇圖片" : "已選擇圖片", 14, false);
        imageStatus.setGravity(Gravity.CENTER_VERTICAL);
        imageStatus.setPadding(dp(12), 0, 0, 0);
        imageRow.addView(imageStatus, weightParams(1));
        root.addView(imageRow);

        Button save = bigButton("儲存卡牌", v -> saveDraftCard());
        root.addView(save);

        addDivider();
        root.addView(sectionTitle("目前卡牌"));
        if (cards.isEmpty()) {
            root.addView(emptyText("目前還沒有卡牌。先新增一張卡，之後才能加入牌組。"));
        } else {
            for (int i = 0; i < cards.size(); i++) {
                root.addView(cardListRow(cards.get(i), i));
            }
        }
    }

    private void pickCardImage() {
        syncDraftFields();
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, REQ_PICK_IMAGE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_PICK_IMAGE && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            final int flags = data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION;
            try {
                getContentResolver().takePersistableUriPermission(uri, flags);
            } catch (Exception ignored) {
                // Some providers do not support persistable permissions. The URI still works during the current session.
            }
            draftCardImageUri = uri.toString();
            showCardManager();
        }
    }

    private void syncDraftFields() {
        if (cardNameInput != null) draftCardName = cardNameInput.getText().toString();
        if (cardCostInput != null) draftCardCost = cardCostInput.getText().toString();
        if (cardDescInput != null) draftCardDesc = cardDescInput.getText().toString();
    }

    private void saveDraftCard() {
        syncDraftFields();
        String name = draftCardName.trim();
        if (name.isEmpty()) {
            toast("請先輸入卡牌名稱");
            return;
        }
        CardData card = new CardData();
        card.id = UUID.randomUUID().toString();
        card.name = name;
        card.cost = draftCardCost.trim();
        card.desc = draftCardDesc.trim();
        card.imageUri = draftCardImageUri;
        cards.add(card);
        draftCardName = "";
        draftCardCost = "";
        draftCardDesc = "";
        draftCardImageUri = "";
        saveData();
        toast("已新增卡牌");
        showCardManager();
    }

    private View cardListRow(CardData card, int index) {
        LinearLayout wrap = panel();
        wrap.setOrientation(LinearLayout.HORIZONTAL);
        wrap.setGravity(Gravity.CENTER_VERTICAL);

        wrap.addView(cardPreview(card, true, 88, 124));

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(dp(12), 0, dp(8), 0);
        TextView name = text(card.name, 17, true);
        TextView cost = text("費用：" + safe(card.cost), 14, false);
        TextView desc = text(card.desc.isEmpty() ? "無描述" : card.desc, 13, false);
        desc.setTextColor(Color.rgb(90, 90, 90));
        info.addView(name);
        info.addView(cost);
        info.addView(desc);
        wrap.addView(info, weightParams(1));

        Button delete = button("刪除", v -> confirmDeleteCard(index));
        wrap.addView(delete, new LinearLayout.LayoutParams(dp(72), dp(52)));
        return wrap;
    }

    private void confirmDeleteCard(int index) {
        CardData card = cards.get(index);
        new AlertDialog.Builder(this)
                .setTitle("刪除卡牌")
                .setMessage("確定刪除「" + card.name + "」？牌組裡引用這張卡的項目也會被移除。")
                .setNegativeButton("取消", null)
                .setPositiveButton("刪除", (d, w) -> {
                    String id = card.id;
                    cards.remove(index);
                    for (DeckData deck : decks) deck.counts.remove(id);
                    saveData();
                    showCardManager();
                })
                .show();
    }

    private void showDeckManager() {
        setBaseScreen("編輯牌組", true, this::showMainMenu);

        root.addView(sectionTitle("牌組"));
        LinearLayout deckActions = row();
        deckActions.addView(button("新增牌組", v -> createDeckDialog()), weightParams(1));
        deckActions.addView(button("改名", v -> renameSelectedDeckDialog()), weightParams(1));
        deckActions.addView(button("刪除", v -> deleteSelectedDeck()), weightParams(1));
        root.addView(deckActions);

        if (decks.isEmpty()) {
            root.addView(emptyText("還沒有牌組。先按「新增牌組」。"));
        } else {
            HorizontalScrollView hsv = new HorizontalScrollView(this);
            LinearLayout deckTabs = new LinearLayout(this);
            deckTabs.setOrientation(LinearLayout.HORIZONTAL);
            for (int i = 0; i < decks.size(); i++) {
                final int idx = i;
                Button b = smallButton((i == selectedDeckIndex ? "● " : "○ ") + decks.get(i).name, v -> {
                    selectedDeckIndex = idx;
                    showDeckManager();
                });
                deckTabs.addView(b);
            }
            hsv.addView(deckTabs);
            root.addView(hsv);
        }

        if (decks.isEmpty()) return;
        DeckData deck = decks.get(clampDeckIndex());
        addDivider();
        root.addView(sectionTitle("目前牌組：「" + deck.name + "」"));

        int total = deck.totalCards();
        TextView totalText = text("總張數：" + total, 15, true);
        totalText.setPadding(0, 0, 0, dp(8));
        root.addView(totalText);

        if (deck.counts.isEmpty()) {
            root.addView(emptyText("這個牌組目前沒有卡牌。從下方卡牌清單加入。"));
        } else {
            ArrayList<String> deadIds = new ArrayList<>();
            for (Map.Entry<String, Integer> entry : deck.counts.entrySet()) {
                CardData card = findCard(entry.getKey());
                if (card == null) {
                    deadIds.add(entry.getKey());
                } else {
                    root.addView(deckEntryRow(deck, card, entry.getValue()));
                }
            }
            if (!deadIds.isEmpty()) {
                for (String id : deadIds) deck.counts.remove(id);
                saveData();
            }
        }

        addDivider();
        root.addView(sectionTitle("加入卡牌"));
        if (cards.isEmpty()) {
            root.addView(emptyText("目前沒有可加入的卡牌。請先到「製作卡牌」新增。"));
        } else {
            for (CardData card : cards) {
                root.addView(addCardToDeckRow(deck, card));
            }
        }
    }

    private int clampDeckIndex() {
        if (selectedDeckIndex < 0) selectedDeckIndex = 0;
        if (selectedDeckIndex >= decks.size()) selectedDeckIndex = Math.max(0, decks.size() - 1);
        return selectedDeckIndex;
    }

    private void createDeckDialog() {
        EditText input = input("牌組名稱", "新牌組");
        new AlertDialog.Builder(this)
                .setTitle("新增牌組")
                .setView(input)
                .setNegativeButton("取消", null)
                .setPositiveButton("新增", (d, w) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) name = "新牌組";
                    DeckData deck = new DeckData();
                    deck.id = UUID.randomUUID().toString();
                    deck.name = name;
                    decks.add(deck);
                    selectedDeckIndex = decks.size() - 1;
                    saveData();
                    showDeckManager();
                })
                .show();
    }

    private void renameSelectedDeckDialog() {
        if (decks.isEmpty()) return;
        DeckData deck = decks.get(clampDeckIndex());
        EditText input = input("牌組名稱", deck.name);
        new AlertDialog.Builder(this)
                .setTitle("牌組改名")
                .setView(input)
                .setNegativeButton("取消", null)
                .setPositiveButton("儲存", (d, w) -> {
                    String name = input.getText().toString().trim();
                    if (!name.isEmpty()) deck.name = name;
                    saveData();
                    showDeckManager();
                })
                .show();
    }

    private void deleteSelectedDeck() {
        if (decks.isEmpty()) return;
        DeckData deck = decks.get(clampDeckIndex());
        new AlertDialog.Builder(this)
                .setTitle("刪除牌組")
                .setMessage("確定刪除「" + deck.name + "」？")
                .setNegativeButton("取消", null)
                .setPositiveButton("刪除", (d, w) -> {
                    decks.remove(deck);
                    clampDeckIndex();
                    saveData();
                    showDeckManager();
                })
                .show();
    }

    private View deckEntryRow(DeckData deck, CardData card, int count) {
        LinearLayout wrap = panel();
        wrap.setOrientation(LinearLayout.HORIZONTAL);
        wrap.setGravity(Gravity.CENTER_VERTICAL);
        wrap.addView(cardPreview(card, true, 64, 90));

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(dp(10), 0, dp(6), 0);
        info.addView(text(card.name, 16, true));
        info.addView(text("數量：" + count, 14, false));
        wrap.addView(info, weightParams(1));

        Button minus = button("－", v -> {
            int n = deck.counts.containsKey(card.id) ? deck.counts.get(card.id) : 0;
            if (n <= 1) deck.counts.remove(card.id); else deck.counts.put(card.id, n - 1);
            saveData();
            showDeckManager();
        });
        Button plus = button("＋", v -> {
            int n = deck.counts.containsKey(card.id) ? deck.counts.get(card.id) : 0;
            deck.counts.put(card.id, n + 1);
            saveData();
            showDeckManager();
        });
        wrap.addView(minus, new LinearLayout.LayoutParams(dp(56), dp(48)));
        wrap.addView(plus, new LinearLayout.LayoutParams(dp(56), dp(48)));
        return wrap;
    }

    private View addCardToDeckRow(DeckData deck, CardData card) {
        LinearLayout wrap = panel();
        wrap.setOrientation(LinearLayout.HORIZONTAL);
        wrap.setGravity(Gravity.CENTER_VERTICAL);
        wrap.addView(cardPreview(card, true, 58, 82));
        TextView name = text(card.name + "　費用：" + safe(card.cost), 15, true);
        name.setPadding(dp(10), 0, 0, 0);
        wrap.addView(name, weightParams(1));
        wrap.addView(button("加入", v -> {
            int n = deck.counts.containsKey(card.id) ? deck.counts.get(card.id) : 0;
            deck.counts.put(card.id, n + 1);
            saveData();
            toast("已加入「" + card.name + "」");
            showDeckManager();
        }), new LinearLayout.LayoutParams(dp(86), dp(48)));
        return wrap;
    }

    private void showUseDeckSelector() {
        setBaseScreen("使用牌組", true, this::showMainMenu);
        if (decks.isEmpty()) {
            root.addView(emptyText("目前沒有牌組。請先到「編輯牌組」建立牌組。"));
            return;
        }
        root.addView(sectionTitle("選擇要使用的牌組"));
        for (DeckData deck : decks) {
            int legalCount = countLegalCards(deck);
            Button b = bigButton(deck.name + "　(" + legalCount + " 張)", v -> startSession(deck));
            b.setEnabled(legalCount > 0);
            root.addView(b);
        }
    }

    private int countLegalCards(DeckData deck) {
        int total = 0;
        for (Map.Entry<String, Integer> entry : deck.counts.entrySet()) {
            if (findCard(entry.getKey()) != null) total += Math.max(0, entry.getValue());
        }
        return total;
    }

    private void startSession(DeckData deck) {
        activeDeck = deck;
        drawPile.clear();
        hand.clear();
        discardPile.clear();
        energyZone.clear();
        handFaceUp = true;
        for (Map.Entry<String, Integer> entry : deck.counts.entrySet()) {
            CardData card = findCard(entry.getKey());
            if (card == null) continue;
            for (int i = 0; i < entry.getValue(); i++) drawPile.add(card);
        }
        Collections.shuffle(drawPile);
        showPlayScreen();
    }

    private void showPlayScreen() {
        setBaseScreen("使用中：「" + (activeDeck == null ? "牌組" : activeDeck.name) + "」", true, this::showUseDeckSelector);

        LinearLayout actions = row();
        actions.addView(button("洗牌", v -> {
            Collections.shuffle(drawPile);
            toast("牌庫已洗牌");
            showPlayScreen();
        }), weightParams(1));
        actions.addView(button(handFaceUp ? "手牌改蓋牌" : "手牌改正面", v -> {
            handFaceUp = !handFaceUp;
            showPlayScreen();
        }), weightParams(1));
        root.addView(actions);

        TextView status = text("牌庫 " + drawPile.size() + "｜手牌 " + hand.size() + "｜棄牌 " + discardPile.size() + "｜能量 " + energyZone.size(), 15, true);
        status.setPadding(0, dp(8), 0, dp(8));
        root.addView(status);

        LinearLayout piles = row();
        piles.addView(deckSourceView(), weightParams(1));
        piles.addView(dropZone("抽到棄牌區", "把牌庫拖到這裡\n＝翻一張進棄牌", "DISCARD"), weightParams(1));
        root.addView(piles);

        root.addView(dropZone("抽到手牌區", "把牌庫拖到這裡\n＝抽一張到手牌", "HAND"), matchWidthParams(dp(96)));

        addDivider();
        root.addView(sectionTitle("手牌區"));
        root.addView(handView());

        LinearLayout lowerZones = row();
        lowerZones.addView(dropZone("棄牌區", "手牌拖到這裡\n＝棄牌", "DISCARD"), weightParams(1));
        lowerZones.addView(dropZone("能量區", "手牌拖到這裡\n＝放置能量", "ENERGY"), weightParams(1));
        root.addView(lowerZones);
        root.addView(dropZone("牌庫底部", "手牌拖到這裡\n＝放到牌庫底部", "DECK_BOTTOM"), matchWidthParams(dp(86)));

        addDivider();
        root.addView(sectionTitle("區域預覽"));
        root.addView(zonePreview("棄牌區", discardPile));
        root.addView(zonePreview("能量區", energyZone));
    }

    private View deckSourceView() {
        LinearLayout box = dropLikeBox("牌庫", drawPile.isEmpty() ? "空牌庫" : "長按拖曳\n剩餘 " + drawPile.size() + " 張");
        box.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                if (drawPile.isEmpty()) {
                    toast("牌庫已空");
                    return true;
                }
                DragPayload payload = new DragPayload("DECK", -1);
                startCompatDrag(v, payload);
                return true;
            }
            return true;
        });
        return box;
    }

    private View handView() {
        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setFillViewport(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(4), 0, dp(8));
        if (hand.isEmpty()) {
            TextView t = emptyText("目前沒有手牌。把牌庫拖到「抽到手牌區」即可抽牌。 ");
            row.addView(t, new LinearLayout.LayoutParams(dp(310), dp(132)));
        } else {
            for (int i = 0; i < hand.size(); i++) {
                final int idx = i;
                View card = cardPreview(hand.get(i), handFaceUp, 112, 158);
                card.setOnTouchListener((v, event) -> {
                    if (event.getAction() == MotionEvent.ACTION_DOWN) {
                        DragPayload payload = new DragPayload("HAND", idx);
                        startCompatDrag(v, payload);
                        return true;
                    }
                    return true;
                });
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(116), dp(166));
                lp.setMargins(0, 0, dp(8), 0);
                row.addView(card, lp);
            }
        }
        hsv.addView(row);
        return hsv;
    }

    private View dropZone(String title, String hint, String zone) {
        LinearLayout box = dropLikeBox(title, hint);
        box.setOnDragListener((v, event) -> {
            switch (event.getAction()) {
                case DragEvent.ACTION_DRAG_ENTERED:
                    v.setBackground(border(Color.rgb(0, 0, 0), Color.rgb(236, 236, 236), dp(14), dp(3)));
                    return true;
                case DragEvent.ACTION_DRAG_EXITED:
                    v.setBackground(border(Color.rgb(40, 40, 40), Color.WHITE, dp(14), dp(2)));
                    return true;
                case DragEvent.ACTION_DROP:
                    Object state = event.getLocalState();
                    if (state instanceof DragPayload) {
                        handleDrop((DragPayload) state, zone);
                    }
                    return true;
                case DragEvent.ACTION_DRAG_ENDED:
                    v.setBackground(border(Color.rgb(40, 40, 40), Color.WHITE, dp(14), dp(2)));
                    return true;
            }
            return true;
        });
        return box;
    }

    private void handleDrop(DragPayload payload, String zone) {
        if ("DECK".equals(payload.source)) {
            if (drawPile.isEmpty()) {
                toast("牌庫已空");
                return;
            }
            CardData card = drawPile.remove(drawPile.size() - 1);
            if ("HAND".equals(zone)) {
                hand.add(card);
                toast("抽到手牌：「" + card.name + "」");
            } else if ("DISCARD".equals(zone)) {
                discardPile.add(card);
                toast("翻到棄牌：「" + card.name + "」");
            } else {
                drawPile.add(card);
                toast("牌庫只能拖到手牌區或棄牌區");
            }
            showPlayScreen();
            return;
        }

        if ("HAND".equals(payload.source)) {
            if (payload.index < 0 || payload.index >= hand.size()) return;
            CardData card = hand.remove(payload.index);
            if ("DISCARD".equals(zone)) {
                discardPile.add(card);
                toast("已棄牌：「" + card.name + "」");
            } else if ("ENERGY".equals(zone)) {
                energyZone.add(card);
                toast("已放入能量區：「" + card.name + "」");
            } else if ("DECK_BOTTOM".equals(zone)) {
                drawPile.add(0, card);
                toast("已放到牌庫底部：「" + card.name + "」");
            } else {
                hand.add(payload.index, card);
                toast("手牌只能拖到棄牌區、能量區或牌庫底部");
            }
            showPlayScreen();
        }
    }

    private View zonePreview(String title, ArrayList<CardData> list) {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(0, dp(4), 0, dp(8));
        wrap.addView(text(title + "：" + list.size() + " 張", 14, true));
        HorizontalScrollView hsv = new HorizontalScrollView(this);
        LinearLayout cardsRow = new LinearLayout(this);
        cardsRow.setOrientation(LinearLayout.HORIZONTAL);
        if (list.isEmpty()) {
            cardsRow.addView(emptyText("空"), new LinearLayout.LayoutParams(dp(80), dp(54)));
        } else {
            int start = Math.max(0, list.size() - 10);
            for (int i = start; i < list.size(); i++) {
                cardsRow.addView(cardPreview(list.get(i), true, 58, 82));
            }
        }
        hsv.addView(cardsRow);
        wrap.addView(hsv);
        return wrap;
    }

    private void startCompatDrag(View v, DragPayload payload) {
        ClipData data = ClipData.newPlainText("card-app-drag", payload.source);
        View.DragShadowBuilder shadow = new View.DragShadowBuilder(v);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            v.startDragAndDrop(data, shadow, payload, 0);
        } else {
            v.startDrag(data, shadow, payload, 0);
        }
    }

    private LinearLayout dropLikeBox(String title, String hint) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(10), dp(10), dp(10), dp(10));
        box.setBackground(border(Color.rgb(40, 40, 40), Color.WHITE, dp(14), dp(2)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(118));
        lp.setMargins(dp(4), dp(4), dp(4), dp(4));
        box.setLayoutParams(lp);
        TextView t = text(title, 16, true);
        t.setGravity(Gravity.CENTER);
        TextView h = text(hint, 13, false);
        h.setGravity(Gravity.CENTER);
        h.setTextColor(Color.rgb(95, 95, 95));
        box.addView(t);
        box.addView(h);
        return box;
    }

    private View cardPreview(CardData card, boolean front, int wDp, int hDp) {
        LinearLayout cardBox = new LinearLayout(this);
        cardBox.setOrientation(LinearLayout.VERTICAL);
        cardBox.setGravity(Gravity.CENTER);
        cardBox.setPadding(dp(5), dp(5), dp(5), dp(5));
        cardBox.setBackground(border(Color.rgb(25, 25, 25), front ? Color.WHITE : Color.rgb(20, 20, 20), dp(10), dp(2)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(wDp), dp(hDp));
        params.setMargins(dp(4), dp(4), dp(4), dp(4));
        cardBox.setLayoutParams(params);

        if (!front) {
            TextView back = text("CARD\nBACK", 16, true);
            back.setTextColor(Color.WHITE);
            back.setGravity(Gravity.CENTER);
            cardBox.addView(back, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            return cardBox;
        }

        TextView cost = text(card.cost == null || card.cost.isEmpty() ? "-" : card.cost, 12, true);
        cost.setGravity(Gravity.END);
        cardBox.addView(cost, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(18)));

        if (card.imageUri != null && !card.imageUri.isEmpty()) {
            ImageView image = new ImageView(this);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            try {
                image.setImageURI(Uri.parse(card.imageUri));
            } catch (Exception ignored) {
            }
            cardBox.addView(image, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        } else {
            TextView placeholder = text("NO\nIMAGE", 12, true);
            placeholder.setGravity(Gravity.CENTER);
            placeholder.setTextColor(Color.rgb(120, 120, 120));
            placeholder.setBackground(border(Color.rgb(210, 210, 210), Color.rgb(245, 245, 245), dp(6), dp(1)));
            cardBox.addView(placeholder, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        }

        TextView name = text(card.name, 12, true);
        name.setGravity(Gravity.CENTER);
        name.setSingleLine(false);
        name.setMaxLines(2);
        cardBox.addView(name, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(36)));
        return cardBox;
    }

    private void setBaseScreen(String title, boolean back, Runnable backAction) {
        ScrollView scrollView = new ScrollView(this);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(24));
        scrollView.addView(root);
        setContentView(scrollView);

        LinearLayout top = row();
        if (back) {
            Button b = smallButton("‹ 返回", v -> {
                if (backAction != null) backAction.run(); else showMainMenu();
            });
            top.addView(b, new LinearLayout.LayoutParams(dp(96), dp(46)));
        }
        TextView titleView = text(title, 24, true);
        titleView.setGravity(back ? Gravity.CENTER_VERTICAL : Gravity.START);
        top.addView(titleView, weightParams(1));
        root.addView(top);
        addSpace(8);
    }

    private TextView sectionTitle(String s) {
        TextView v = text(s, 18, true);
        v.setPadding(0, dp(10), 0, dp(8));
        return v;
    }

    private TextView label(String s) {
        TextView v = text(s, 13, true);
        v.setTextColor(Color.rgb(70, 70, 70));
        v.setPadding(dp(2), dp(6), 0, dp(3));
        return v;
    }

    private EditText input(String hint, String value) {
        EditText e = new EditText(this);
        e.setText(value == null ? "" : value);
        e.setHint(hint);
        e.setSingleLine(false);
        e.setTextSize(16);
        e.setPadding(dp(10), dp(8), dp(10), dp(8));
        e.setBackground(border(Color.rgb(160, 160, 160), Color.WHITE, dp(10), dp(1)));
        return e;
    }

    private TextView text(String s, int sp, boolean bold) {
        TextView v = new TextView(this);
        v.setText(s == null ? "" : s);
        v.setTextSize(sp);
        v.setTextColor(Color.rgb(20, 20, 20));
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return v;
    }

    private TextView emptyText(String s) {
        TextView v = text(s, 14, false);
        v.setTextColor(Color.rgb(95, 95, 95));
        v.setGravity(Gravity.CENTER);
        v.setPadding(dp(10), dp(14), dp(10), dp(14));
        v.setBackground(border(Color.rgb(220, 220, 220), Color.rgb(250, 250, 250), dp(10), dp(1)));
        return v;
    }

    private Button bigButton(String s, View.OnClickListener listener) {
        Button b = button(s, listener);
        b.setTextSize(18);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(58));
        lp.setMargins(0, dp(6), 0, dp(6));
        b.setLayoutParams(lp);
        return b;
    }

    private Button smallButton(String s, View.OnClickListener listener) {
        Button b = button(s, listener);
        b.setTextSize(14);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(46));
        lp.setMargins(dp(3), dp(3), dp(3), dp(3));
        b.setLayoutParams(lp);
        return b;
    }

    private Button button(String s, View.OnClickListener listener) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setBackground(border(Color.rgb(20, 20, 20), Color.rgb(20, 20, 20), dp(12), dp(1)));
        b.setOnClickListener(listener);
        return b;
    }

    private LinearLayout row() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        return r;
    }

    private LinearLayout panel() {
        LinearLayout p = new LinearLayout(this);
        p.setPadding(dp(8), dp(8), dp(8), dp(8));
        p.setBackground(border(Color.rgb(230, 230, 230), Color.rgb(250, 250, 250), dp(12), dp(1)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(5), 0, dp(5));
        p.setLayoutParams(lp);
        return p;
    }

    private void addDivider() {
        View line = new View(this);
        line.setBackgroundColor(Color.rgb(220, 220, 220));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        lp.setMargins(0, dp(18), 0, dp(8));
        root.addView(line, lp);
    }

    private void addSpace(int dp) {
        Space space = new Space(this);
        root.addView(space, new LinearLayout.LayoutParams(1, dp(dp)));
    }

    private LinearLayout.LayoutParams weightParams(float weight) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight);
        lp.setMargins(dp(3), dp(3), dp(3), dp(3));
        return lp;
    }

    private LinearLayout.LayoutParams matchWidthParams(int heightPx) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, heightPx);
        lp.setMargins(dp(4), dp(4), dp(4), dp(4));
        return lp;
    }

    private GradientDrawable border(int stroke, int fill, int radius, int width) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(fill);
        gd.setStroke(width, stroke);
        gd.setCornerRadius(radius);
        return gd;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    private String safe(String s) {
        return s == null || s.trim().isEmpty() ? "-" : s;
    }

    private CardData findCard(String id) {
        for (CardData card : cards) if (card.id.equals(id)) return card;
        return null;
    }

    private void loadData() {
        cards.clear();
        decks.clear();
        SharedPreferences pref = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        String raw = pref.getString(PREF_KEY, "");
        if (raw == null || raw.isEmpty()) {
            createStarterData();
            saveData();
            return;
        }
        try {
            JSONObject db = new JSONObject(raw);
            JSONArray cardArr = db.optJSONArray("cards");
            if (cardArr != null) {
                for (int i = 0; i < cardArr.length(); i++) cards.add(CardData.fromJson(cardArr.getJSONObject(i)));
            }
            JSONArray deckArr = db.optJSONArray("decks");
            if (deckArr != null) {
                for (int i = 0; i < deckArr.length(); i++) decks.add(DeckData.fromJson(deckArr.getJSONObject(i)));
            }
        } catch (Exception e) {
            createStarterData();
            saveData();
        }
    }

    private void saveData() {
        try {
            JSONObject db = new JSONObject();
            JSONArray cardArr = new JSONArray();
            for (CardData card : cards) cardArr.put(card.toJson());
            JSONArray deckArr = new JSONArray();
            for (DeckData deck : decks) deckArr.put(deck.toJson());
            db.put("cards", cardArr);
            db.put("decks", deckArr);
            getSharedPreferences(PREF_NAME, MODE_PRIVATE).edit().putString(PREF_KEY, db.toString()).apply();
        } catch (Exception e) {
            toast("儲存失敗：" + e.getMessage());
        }
    }

    private void createStarterData() {
        CardData c1 = new CardData();
        c1.id = UUID.randomUUID().toString();
        c1.name = "範例攻擊";
        c1.cost = "1";
        c1.desc = "這是一張範例卡，可以刪除。";
        c1.imageUri = "";
        CardData c2 = new CardData();
        c2.id = UUID.randomUUID().toString();
        c2.name = "範例防禦";
        c2.cost = "2";
        c2.desc = "用來測試牌組與拖曳。";
        c2.imageUri = "";
        cards.add(c1);
        cards.add(c2);

        DeckData d = new DeckData();
        d.id = UUID.randomUUID().toString();
        d.name = "範例牌組";
        d.counts.put(c1.id, 3);
        d.counts.put(c2.id, 2);
        decks.add(d);
    }

    private static class DragPayload {
        final String source;
        final int index;
        DragPayload(String source, int index) {
            this.source = source;
            this.index = index;
        }
    }

    private static class CardData {
        String id = "";
        String name = "";
        String cost = "";
        String desc = "";
        String imageUri = "";

        JSONObject toJson() throws JSONException {
            JSONObject o = new JSONObject();
            o.put("id", id);
            o.put("name", name);
            o.put("cost", cost);
            o.put("desc", desc);
            o.put("imageUri", imageUri);
            return o;
        }

        static CardData fromJson(JSONObject o) {
            CardData c = new CardData();
            c.id = o.optString("id", UUID.randomUUID().toString());
            c.name = o.optString("name", "未命名卡牌");
            c.cost = o.optString("cost", "");
            c.desc = o.optString("desc", "");
            c.imageUri = o.optString("imageUri", "");
            return c;
        }
    }

    private static class DeckData {
        String id = "";
        String name = "";
        LinkedHashMap<String, Integer> counts = new LinkedHashMap<>();

        int totalCards() {
            int total = 0;
            for (Integer n : counts.values()) total += Math.max(0, n == null ? 0 : n);
            return total;
        }

        JSONObject toJson() throws JSONException {
            JSONObject o = new JSONObject();
            o.put("id", id);
            o.put("name", name);
            JSONObject c = new JSONObject();
            for (Map.Entry<String, Integer> e : counts.entrySet()) c.put(e.getKey(), e.getValue());
            o.put("counts", c);
            return o;
        }

        static DeckData fromJson(JSONObject o) {
            DeckData d = new DeckData();
            d.id = o.optString("id", UUID.randomUUID().toString());
            d.name = o.optString("name", "未命名牌組");
            JSONObject c = o.optJSONObject("counts");
            if (c != null) {
                JSONArray names = c.names();
                if (names != null) {
                    for (int i = 0; i < names.length(); i++) {
                        String key = names.optString(i, "");
                        if (!key.isEmpty()) d.counts.put(key, Math.max(0, c.optInt(key, 0)));
                    }
                }
            }
            return d;
        }
    }
}
