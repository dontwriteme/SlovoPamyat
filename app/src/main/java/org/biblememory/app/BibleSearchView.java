package org.biblememory.app;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.style.BackgroundColorSpan;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.biblememory.app.ScreenUi.*;

/** Search entry and virtualized rows. Navigation and search execution are supplied by the host. */
final class BibleSearchView {
    private final ScreenUi ui;
    private final Consumer<BibleSearchLogic.Hit> openHit;

    BibleSearchView(ScreenUi ui,Consumer<BibleSearchLogic.Hit> openHit){
        this.ui=ui;
        this.openHit=openHit;
    }

    /** Search is the first scrolling item, both in the catalogue and the search results. */
    void searchBar(LinearLayout into,BibleSearchSession session,boolean inPicker){
        LinearLayout outer=ui.column();
        if(inPicker)outer.setBackgroundColor(BG);
        outer.setPadding(0,ui.dp(8),0,ui.dp(8));
        LinearLayout bar=ui.row();bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(ui.dp(14),ui.dp(3),ui.dp(7),ui.dp(3));
        bar.setMinimumHeight(ui.dp(58));
        GradientDrawable frame=ui.shape(0xFFFFFDF9,17);
        frame.setStroke(ui.dp(2),0xFFCE9072);
        bar.setBackground(frame);bar.setElevation(ui.dp(2));
        NavigationGlyph glass=new NavigationGlyph(into.getContext(),"search",GREEN);
        bar.addView(glass,new LinearLayout.LayoutParams(ui.dp(29),ui.dp(29)));
        EditText query=ui.input("Поиск по словам стиха",false);
        query.setTextSize(17);query.setSingleLine(true);
        query.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        query.setPadding(ui.dp(12),ui.dp(9),ui.dp(5),ui.dp(9));
        query.setBackgroundColor(Color.TRANSPARENT);
        query.setText(session.query());query.setSelection(query.length());
        query.setContentDescription("Поиск по словам Библии");
        bar.addView(query,new LinearLayout.LayoutParams(0,-2,1));
        TextView erase=ui.txt("×",24,MUTED,false);erase.setGravity(Gravity.CENTER);
        erase.setContentDescription("Очистить поиск по Библии");
        erase.setVisibility(session.query().isEmpty()?View.GONE:View.VISIBLE);
        bar.addView(erase,new LinearLayout.LayoutParams(ui.dp(40),ui.dp(45)));
        erase.setOnClickListener(v->query.setText(""));
        // The magnifier submits too; the keyboard search action does the same.
        glass.setClickable(true);
        glass.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        glass.setContentDescription("Найти по Библии");
        glass.setOnClickListener(v->session.submit(query.getText().toString()));
        query.setOnEditorActionListener((v,action,event)->{
            if(action==EditorInfo.IME_ACTION_SEARCH){
                session.submit(query.getText().toString());return true;
            }
            return false;
        });
        query.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence s,int st,int count,int after){}
            public void onTextChanged(CharSequence s,int start,int before,int count){
                erase.setVisibility(s.length()==0?View.GONE:View.VISIBLE);
                if(query.isAttachedToWindow())session.changed(s.toString());
            }
            public void afterTextChanged(Editable e){}
        });
        outer.addView(bar,new LinearLayout.LayoutParams(-1,-2));
        into.addView(outer,new LinearLayout.LayoutParams(-1,-2));
    }

    static EditText findInput(View view){
        if(view instanceof EditText&&"Поиск по словам Библии".contentEquals(view.getContentDescription()==null?"":view.getContentDescription()))return (EditText)view;
        if(view instanceof ViewGroup){
            ViewGroup group=(ViewGroup)view;
            for(int i=0;i<group.getChildCount();i++){
                EditText found=findInput(group.getChildAt(i));if(found!=null)return found;
            }
        }
        return null;
    }
    static void preserveInput(Supplier<View> root,Runnable render){
        EditText old=findInput(root.get());
        boolean focused=old!=null&&old.hasFocus();
        int cursor=old==null?0:Math.max(0,old.getSelectionStart());
        int selectionEnd=old==null?0:Math.max(0,old.getSelectionEnd());
        render.run();
        if(focused){
            EditText next=findInput(root.get());
            if(next!=null){next.requestFocus();next.setSelection(Math.min(cursor,next.length()),Math.min(selectionEnd,next.length()));}
        }
    }
    boolean updateList(LinearLayout root,BibleSearchSession session,String message){
        if(root!=null&&root.getChildCount()>0&&root.getChildAt(0) instanceof ListView){
            Object state=root.getChildAt(0).getTag();
            if(state instanceof SearchListState){
                ((SearchListState)state).replace(session.hits(),session.query(),message);
                return true;
            }
        }
        return false;
    }
    private CharSequence highlightSearchWords(String source,BibleSearchLogic.WordMatcher words){
        android.text.SpannableString span=new android.text.SpannableString(source);
        java.util.regex.Matcher matcher=BibleSearchLogic.WORDS.matcher(source);
        while(matcher.find())if(words.matches(matcher.group()))
            span.setSpan(new BackgroundColorSpan(0xFFFFE4BD),matcher.start(),matcher.end(),android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return span;
    }
    private static final class SearchListState {
        List<BibleSearchLogic.Hit> hits;
        BibleSearchLogic.WordMatcher words;
        TextView summary;
        BaseAdapter adapter;
        final android.util.LruCache<Integer,CharSequence> highlights=new android.util.LruCache<>(128);
        SearchListState(List<BibleSearchLogic.Hit> hits,String query){this.hits=hits;words=new BibleSearchLogic.WordMatcher(query);}
        void replace(List<BibleSearchLogic.Hit> results,String query,String message){
            hits=results;words=new BibleSearchLogic.WordMatcher(query);highlights.evictAll();
            summary.setText(message!=null?message:results.isEmpty()?"Ничего не найдено":"Найдено стихов: "+results.size());
            adapter.notifyDataSetChanged();
        }
    }

    void placeholders(LinearLayout content,BibleSearchSession session) {

        if (session.loading()) {
            ui.helper(content, "Поиск...");
            return;
        }

        if (!session.error().isEmpty()) {
            ui.helper(content, session.error());

            ui.action(
                    content,
                    "Повторить поиск",
                    false,
                    () -> session.submit(session.query())
            );
            return;
        }

        if (session.hits().isEmpty()) {
            ui.helper(
                    content,
                    "Ничего не найдено"
            );
        }

        // При наличии результатов список создаётся
        // отдельно, методом createSearchResultsList().
    }


    private static final class SearchRowHolder {
        TextView reference;
        TextView verse;
        TextView open;
    }

    ListView results(Context context,BibleSearchSession session,boolean inPicker) {

        ListView list = new ListView(context);

        list.setBackgroundColor(BG);
        list.setDivider(null);
        list.setVerticalScrollBarEnabled(false);
        list.setClipToPadding(false);
        // The scrolling list header contains a usable EditText search field.
        list.setItemsCanFocus(true);

        final SearchListState state=new SearchListState(session.hits(),session.query());
        list.setTag(state);
        // A ListView is used for large results. Its search field is a header so
        // the field scrolls away with result rows instead of remaining pinned.
        LinearLayout searchHeader=ui.column();
        searchHeader.setPadding(ui.dp(19),0,ui.dp(19),0);
        searchBar(searchHeader,session,inPicker);
        list.addHeaderView(searchHeader,null,false);
        // Показываем реальное число ВСЕХ результатов.
        TextView summary = ui.txt(
                state.hits.isEmpty()?"Ничего не найдено":"Найдено стихов: " + state.hits.size(),
                14,
                MUTED,
                true
        );

        summary.setPadding(
                ui.dp(19), ui.dp(15),
                ui.dp(19), ui.dp(11)
        );

        list.addHeaderView(summary, null, false);

        state.summary=summary;
        BaseAdapter adapter=new BaseAdapter() {

            @Override
            public int getCount() {
                return state.hits.size();
            }

            @Override
            public Object getItem(int position) {
                return state.hits.get(position);
            }

            @Override
            public long getItemId(int position) {
                return position;
            }

            @Override
            public View getView(
                    int position,
                    View convertView,
                    ViewGroup parent) {

                SearchRowHolder holder;
                View result = convertView;

                if (result == null) {

                    LinearLayout outer = ui.column();

                    outer.setPadding(
                            ui.dp(19), ui.dp(4),
                            ui.dp(19), ui.dp(7)
                    );

                    LinearLayout card = ui.column();

                    card.setPadding(
                            ui.dp(15), ui.dp(13),
                            ui.dp(15), ui.dp(13)
                    );

                    card.setBackground(
                            ui.outlined(CARD, 18, BORDER)
                    );

                    LinearLayout heading = ui.row();

                    heading.setGravity(
                            Gravity.CENTER_VERTICAL
                    );

                    TextView reference = ui.txt(
                            "", 15, GREEN, true
                    );

                    heading.addView(
                            reference,
                            new LinearLayout.LayoutParams(
                                    0, -2, 1
                            )
                    );

                    card.addView(heading);

                    TextView verse = ui.prose("", 16);

                    LinearLayout.LayoutParams vp =
                            new LinearLayout.LayoutParams(
                                    -1, -2
                            );

                    vp.topMargin = ui.dp(8);
                    card.addView(verse, vp);

                    TextView open = ui.txt(
                            "Показать в главе  ›",
                            13,
                            GREEN,
                            true
                    );

                    open.setPadding(
                            0, ui.dp(11), 0, ui.dp(8)
                    );

                    LinearLayout.LayoutParams op =
                            new LinearLayout.LayoutParams(
                                    -1, ui.dp(48)
                            );

                    op.topMargin = ui.dp(3);
                    card.addView(open, op);

                    outer.addView(
                            card,
                            new LinearLayout.LayoutParams(
                                    -1, -2
                            )
                    );

                    holder = new SearchRowHolder();

                    holder.reference = reference;
                    holder.verse = verse;
                    holder.open = open;

                    outer.setTag(holder);

                    result = outer;

                } else {
                    holder = (SearchRowHolder) result.getTag();
                }

                BibleSearchLogic.Hit hit =
                        state.hits.get(position);

                holder.reference.setText(hit.reference);
                holder.reference.setTextColor(
                        ui.bookInk(hit.book)
                );

                // Подсветка выполняется только для
                // строк, которые сейчас видны.
                CharSequence highlighted=state.highlights.get(position);
                if(highlighted==null){highlighted=highlightSearchWords(hit.text,state.words);state.highlights.put(position,highlighted);}
                holder.verse.setText(highlighted);

                holder.open.setOnClickListener(v -> openHit.accept(hit));

                return result;
            }
        };
        state.adapter=adapter;list.setAdapter(adapter);
        return list;
    }


}
