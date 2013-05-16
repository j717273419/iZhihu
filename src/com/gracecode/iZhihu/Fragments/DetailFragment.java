package com.gracecode.iZhihu.Fragments;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Picture;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.*;
import com.gracecode.iZhihu.Dao.Question;
import com.gracecode.iZhihu.Dao.QuestionsDatabase;
import com.gracecode.iZhihu.Dao.ThumbnailsDatabase;
import com.gracecode.iZhihu.R;
import com.gracecode.iZhihu.Util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DetailFragment extends WebViewFragment {
    private static final String TAG = DetailFragment.class.getName();
    private static final String KEY_SCROLL_BY = "key_scroll_by_";
    private static final String TEMPLATE_DETAIL_FILE = "detail.html";
    private static final String URL_ASSETS_PREFIX = "file:///android_asset/";
    private static final String MIME_HTML_TYPE = "text/html";
    public static final int ID_NOT_FOUND = 0;
    private static final int DONT_HAVE_SCROLLY = 0;
    private static final long AUTO_SCROLL_DELAY = 500;
    private static final int FIVE_MINUTES = 1000 * 60 * 5;

    private final int id;
    private final Context context;
    private Activity activity;
    private static QuestionsDatabase questionsDatabase;
    private static ThumbnailsDatabase thumbnailsDatabase;
    private SharedPreferences sharedPreferences;

    private Question question;
    private boolean isNeedIndent = false;
    private boolean isNeedReplaceSymbol = false;
    private boolean isNeedCacheThumbnails = true;
    private boolean isShareByTextOnly = false;

    private WebViewClient webViewClient = new WebViewClient() {
        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            // @todo 记忆滚动需要优化
            //decideAutoScroll();
            if (!isShareByTextOnly && Util.isExternalStorageExists()) {
                new Thread(genScreenShots).start();
            }
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            Util.openWithBrowser(getActivity(), url);
            return true;
        }
    };


    private Runnable genScreenShots = new Runnable() {
        @Override
        public void run() {
            Bitmap bitmap = null;
            try {
                Thread.sleep(2000);
                if (!isTempScreenShotsFileCached()) {
                    File screenShotsFile = getTempScreenShotsFile();
                    FileOutputStream fileOutPutStream = new FileOutputStream(screenShotsFile);
                    bitmap = genCaptureBitmap();
                    if (bitmap != null && !bitmap.isRecycled()) {
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, fileOutPutStream);
                        fileOutPutStream.flush();
                        fileOutPutStream.close();
                        Log.d(TAG, "Generated screenshots at " + screenShotsFile.getAbsolutePath());
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            } catch (OutOfMemoryError e) {
                // @todo mark do not generate again.
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                if (bitmap != null && !bitmap.isRecycled()) {
                    bitmap.recycle();
                }
            }
        }
    };

    public boolean isTempScreenShotsFileCached() {
        File tempScreenShotsFile = getTempScreenShotsFile();
        Boolean is5MinutesAgo = (System.currentTimeMillis() - tempScreenShotsFile.lastModified()) < FIVE_MINUTES;
        return tempScreenShotsFile.exists() && tempScreenShotsFile.length() > 0 && is5MinutesAgo;

    }

    public File getTempScreenShotsFile() {
        return new File(context.getCacheDir(), question.answerId + ".png");
    }

    private String getTemplateString() {
        String template;
        try {
            template = Util.getFileContent(activity.getAssets().open(TEMPLATE_DETAIL_FILE));
        } catch (IOException e) {
            return "%s";
        }

        return template;
    }

    public DetailFragment(int id, Context context) {
        this.id = id;
        this.context = context;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return super.onCreateView(inflater, container, savedInstanceState);
    }

    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        this.activity = getActivity();
        this.sharedPreferences = PreferenceManager.getDefaultSharedPreferences(activity);
        questionsDatabase = new QuestionsDatabase(context);
        thumbnailsDatabase = new ThumbnailsDatabase(context);

        this.isNeedIndent = sharedPreferences.getBoolean(getString(R.string.key_indent), false);
        this.isNeedReplaceSymbol = sharedPreferences.getBoolean(getString(R.string.key_symbol), true);
        this.isNeedCacheThumbnails = sharedPreferences.getBoolean(getString(R.string.key_enable_cache), true);
        this.isShareByTextOnly = sharedPreferences.getBoolean(getString(R.string.key_share_text_only), false);

        try {
            question = questionsDatabase.getSingleQuestion(id);
        } catch (QuestionsDatabase.QuestionNotFoundException e) {
            Util.showLongToast(context, e.getMessage());
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        if (question == null) {
            return;
        }
        String data = String.format(getTemplateString(),
                getClassName(),
                isNeedReplaceSymbol ? Util.replaceSymbol(question.title) : question.title,
                formatContent(question.description),
                question.userName,
                "<p class='update-at'>" + question.updateAt + "</p>" + formatContent(question.content));

//        getWebView().setScrollbarFadingEnabled(false);
        getWebView().setScrollBarStyle(WebView.SCROLLBARS_INSIDE_OVERLAY);

        getWebView().getSettings().setLoadWithOverviewMode(true);
        getWebView().getSettings().setUseWideViewPort(true);
        getWebView().getSettings().setJavaScriptEnabled(true);

        getWebView().loadDataWithBaseURL(URL_ASSETS_PREFIX, data, MIME_HTML_TYPE, Util.DEFAULT_CHARSET, null);
        getWebView().setWebViewClient(webViewClient);

        getWebView().setWebChromeClient(new WebChromeClient() {
            public boolean onConsoleMessage(ConsoleMessage cm) {
                Log.d(TAG, cm.message() + "\nFrom line " + cm.lineNumber() + " of " + cm.sourceId());
                return true;
            }
        });
    }


    private String getKeyScrollById() {
        return KEY_SCROLL_BY + this.id;
    }

    /**
     * 判断是否需要自动滚动
     */
    private void decideAutoScroll() {
        final int savedScrollY = sharedPreferences.getInt(getKeyScrollById(), DONT_HAVE_SCROLLY);
        boolean needAutoScroll = sharedPreferences.getBoolean(getString(R.string.key_auto_scroll), true);

        if (needAutoScroll && savedScrollY != DONT_HAVE_SCROLLY) {
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    getWebView().scrollTo(0, savedScrollY);
                }
            }, AUTO_SCROLL_DELAY);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();

        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putInt(getKeyScrollById(), getWebView().getScrollY());
        editor.commit();
    }

    private String formatParagraph(String content) {
        Pattern pattern = Pattern.compile("<hr>");
        Matcher matcher = pattern.matcher(content);
        content = matcher.replaceAll("</p><p>");

        pattern = Pattern.compile("<p>\\s+");
        matcher = pattern.matcher(content);
        content = matcher.replaceAll("<p>");

        pattern = Pattern.compile("<p></p>");
        matcher = pattern.matcher(content);
        content = matcher.replaceAll("");

        content = "<p>" + content + "</p>";

        return content;
    }

    private String formatContent(String content) {
        content = formatParagraph(content);

        if (isNeedReplaceSymbol) {
            content = Util.replaceSymbol(content);
        }

        if (isNeedCacheThumbnails) {
            List<String> imageUrls = Util.getImageUrls(content);
            for (String url : imageUrls) {
                if (thumbnailsDatabase.isCached(url)) {
                    String localPathString = thumbnailsDatabase.getCachedPath(url);
                    Log.v(TAG, "Found offline cache file, replace online image " + url + " with file://" + localPathString);
                    content = content.replace(url, "file://" + localPathString);
                } else {
                    thumbnailsDatabase.add(url);
                }
            }
        }

        return content;
    }

    private String getClassName() {
        String className = "";
        int fontSize = Integer.parseInt(
                sharedPreferences.getString(getString(R.string.key_font_size), getString(R.string.default_font_size)));

        switch (fontSize) {
            case 12:
                className += " tiny";
                break;
            case 14:
                className += " small";
                break;
            case 16:
                className += " normal";
                break;
            case 18:
                className += " big";
                break;
            case 20:
                className += " huge";
                break;
            default:
                className += " normal";
        }

        if (isNeedIndent) {
            className += " indent";
        }

        return className;
    }

    public boolean isStared() {
        return (question != null) && question.isStared();
    }

    public int getQuestionId() {
        if (question != null) {
            return question.questionId;
        } else {
            return ID_NOT_FOUND;
        }
    }

    /**
     * 截取所有网页内容到 Bitmap
     *
     * @return Bitmap
     */
    Bitmap genCaptureBitmap() throws OutOfMemoryError {
        // @todo Future versions of WebView may not support use on other threads.
        try {
            Picture picture = getWebView().capturePicture();
            int height = picture.getHeight(), width = picture.getWidth();
            if (height == 0 || width == 0) {
                return null;
            }
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            picture.draw(canvas);
            return bitmap;
        } catch (NullPointerException e) {
            return null;
        }
    }

    public boolean markStar(boolean status) {
//        if (questionsDatabase != null) {
//            return questionsDatabase.markQuestionAsStared(id, status) > 0 ? true : false;
//        }
//
//        return false;

        return (questionsDatabase != null) && (questionsDatabase.markQuestionAsStared(id, status) > 0);
    }

    public boolean markAsRead() {
        return (questionsDatabase != null) && (questionsDatabase.markSingleQuestionAsReaded(id) > 0);
    }

    public String getShareString() {
        return String.format("%s #%s# %s", question.title, context.getString(R.string.app_name), getOnlineShortUrl(question.answerId));
    }

    private static String getOnlineShortUrl(int number) {
        String s = "", KEY = "6BCMx(0gEwTj3FbUGPe7rtKfqosmZOX2S)5IvH.zu9DdQRL41AnV8ckylhp!YNWJi";
        int l = KEY.length();

        while (number > 0) {
            int x = number % l;
            s = KEY.substring(x, x + 1) + s;
            number = (int) Math.floor(number / l);
        }

        return "http://z.ihu.im/u/" + s;
    }


    @Override
    public void onDestroy() {
//        if (questionsDatabase != null) {
//            questionsDatabase.close();
//            questionsDatabase = null;
//        }
//
//        if (thumbnailsDatabase != null) {
//            thumbnailsDatabase.close();
//            thumbnailsDatabase = null;
//        }
        super.onDestroy();
    }
}
