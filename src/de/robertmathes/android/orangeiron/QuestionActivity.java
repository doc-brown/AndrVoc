package de.robertmathes.android.orangeiron;

import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.TextView;
import de.robertmathes.android.orangeiron.adapter.QuestionListViewAdapter;
import de.robertmathes.android.orangeiron.db.DataSource;
import de.robertmathes.android.orangeiron.model.Lesson;
import de.robertmathes.android.orangeiron.model.User;

public class QuestionActivity extends Activity implements OnItemClickListener {

    private AppPreferences appPrefs;
    private DataSource db;
    private User user;
    private Lesson lesson;
    private int currentWord;
    private TextView originalWord;
    private ListView translations;
    private TextView correctAnswers;
    private TextView badAnswers;
    private int correctAnswersCount = 0;
    private int badAnswersCount = 0;
    private QuestionListViewAdapter translationsAdapter;
    private ObjectAnimator correctAnswersAnimation;
    private ObjectAnimator wrongAnswersAnimation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            setContentView(R.layout.activity_question_land);
        } else {
            setContentView(R.layout.activity_question);
        }

        appPrefs = new AppPreferences(getApplicationContext());

        originalWord = (TextView) findViewById(R.id.textView_lesson_originalWord);
        translations = (ListView) findViewById(R.id.listView_lesson_translations);
        translations.setOnItemClickListener(this);
        correctAnswers = (TextView) findViewById(R.id.textView_lesson_correctAnswers);
        correctAnswers.setText(correctAnswersCount + "");
        badAnswers = (TextView) findViewById(R.id.textView_lesson_wrong_answers);
        badAnswers.setText(badAnswersCount + "");

        currentWord = 0;

        // setup animations
        // Scale the button in X and Y. Note the use of PropertyValuesHolder to animate
        // multiple properties on the same object in parallel.
        PropertyValuesHolder pvhX = PropertyValuesHolder.ofFloat(View.SCALE_X, 2);
        PropertyValuesHolder pvhY = PropertyValuesHolder.ofFloat(View.SCALE_Y, 2);
        // PropertyValuesHolder pvhGreen = PropertyValuesHolder.ofInt("textColor", Color.GREEN);
        correctAnswersAnimation =
                ObjectAnimator.ofPropertyValuesHolder(correctAnswers, pvhX, pvhY/* , pvhGreen */);
        correctAnswersAnimation.setRepeatCount(1);
        correctAnswersAnimation.setRepeatMode(ValueAnimator.REVERSE);
        wrongAnswersAnimation = ObjectAnimator.ofPropertyValuesHolder(badAnswers, pvhX, pvhY);
        wrongAnswersAnimation.setRepeatCount(1);
        wrongAnswersAnimation.setRepeatMode(ValueAnimator.REVERSE);

    }

    @Override
    protected void onStart() {
        super.onStart();

        // Open database
        if (db == null) {
            db = new DataSource(getApplicationContext());
        }
        db.open();
    }

    @Override
    protected void onResume() {
        super.onResume();

        Intent intent = getIntent();
        int lessonMode = intent.getIntExtra(Lesson.LESSON_MODE, Lesson.LESSON_MODE_NORMAL);

        // get the current user and lesson (based on the lesson mode)
        user = db.getUser(appPrefs.getCurrentUser());
        switch (lessonMode) {
            case Lesson.LESSON_MODE_NORMAL:
                lesson = db.getLessonById(appPrefs.getCurrentLesson());
                break;
            case Lesson.LESSON_MODE_WEAKEST_WORDS:
                lesson = db.getWeakestWordsByUser(user, 20);
                break;
            case Lesson.LESSON_MODE_OLDEST_WORDS:
                lesson = db.getOldestWordsByUser(user, 20);
                break;
            default:
                lesson = db.getLessonById(appPrefs.getCurrentLesson());
        }

        // set the list view adapter
        translationsAdapter = new QuestionListViewAdapter(getApplicationContext(), lesson.getVocabulary().get(0));
        translations.setAdapter(translationsAdapter);

        // set the title based on the current lesson
        setTitle(getString(R.string.title_lesson) + " " + lesson.getName());

        originalWord.setText(lesson.getVocabulary().get(currentWord).getOriginalWord());
    }

    @Override
    protected void onStop() {
        super.onStop();

        // Close the db connection
        db.close();
    }

    private void updateCorrectAnswerCount() {
        correctAnswersCount++;
        db.updateCorrectAnswerCount(user.getId(), lesson.getVocabulary().get(currentWord).getLessonId(), lesson.getVocabulary().get(currentWord).getId());
        correctAnswers.setText(correctAnswersCount + "");
    }

    private void updateWrongAnswerCount() {
        badAnswersCount++;
        db.updateBadAnswerCount(user.getId(), lesson.getVocabulary().get(currentWord).getLessonId(), lesson.getVocabulary().get(currentWord).getId());
        badAnswers.setText(badAnswersCount + "");
    }

    @Override
    public void onItemClick(AdapterView<?> adapterView, final View view, int position, long id) {
        if (translationsAdapter.getItem(position).equals(lesson.getVocabulary().get(currentWord).getCorrectTranslation())) {
            correctAnswersAnimation.addListener(new AnimatorListener() {

                @Override
                public void onAnimationEnd(Animator animation) {
                    setDefaultTextColor(view);
                    moveToNextWord();
                }

                @Override
                public void onAnimationCancel(Animator animation) {
                }

                @Override
                public void onAnimationRepeat(Animator animation) {
                }

                @Override
                public void onAnimationStart(Animator animation) {
                }
            });
            markCorrectAnswer(view);
            updateCorrectAnswerCount();
            correctAnswersAnimation.start();

        } else {
            updateWrongAnswerCount();
            markWrongAnswer(view);
            wrongAnswersAnimation.addListener(new AnimatorListener() {

                @Override
                public void onAnimationStart(Animator animation) {
                }

                @Override
                public void onAnimationRepeat(Animator animation) {
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    setDefaultTextColor(view);
                }

                @Override
                public void onAnimationCancel(Animator animation) {
                }
            });
            wrongAnswersAnimation.start();
        }
    }

    private void moveToNextWord() {
        currentWord++;
        if (currentWord < lesson.getVocabulary().size()) {
            originalWord.setText(lesson.getVocabulary().get(currentWord).getOriginalWord());
            translationsAdapter.setWord(lesson.getVocabulary().get(currentWord));
            translationsAdapter.notifyDataSetChanged();
        } else {
            finish();
        }
    }

    private void markWrongAnswer(View view) {
        TextView textView = (TextView) view.findViewById(R.id.translation);
        textView.setTextColor(Color.RED);
    }

    private void markCorrectAnswer(View view) {
        TextView textView = (TextView) view.findViewById(R.id.translation);
        textView.setTextColor(Color.GREEN);
    }

    private void setDefaultTextColor(View view) {
        TextView textView = (TextView) view.findViewById(R.id.translation);
        textView.setTextColor(Color.BLACK);
    }
}
