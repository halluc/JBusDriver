package me.jbusdriver.ui.activity

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.support.design.widget.FloatingActionButton
import android.support.v4.content.res.ResourcesCompat
import android.support.v4.graphics.ColorUtils
import android.support.v4.graphics.drawable.DrawableCompat
import android.support.v7.widget.Toolbar
import android.view.Menu
import android.view.MenuItem
import android.view.View
import com.afollestad.materialdialogs.MaterialDialog
import com.bumptech.glide.request.target.DrawableImageViewTarget
import com.gyf.barlibrary.ImmersionBar
import io.reactivex.Flowable
import io.reactivex.rxkotlin.addTo
import jbusdriver.me.jbusdriver.R
import kotlinx.android.synthetic.main.activity_movie_detail.*
import kotlinx.android.synthetic.main.content_movie_detail.*
import kotlinx.android.synthetic.main.layout_load_magnet.view.*
import me.jbusdriver.common.*
import me.jbusdriver.db.bean.History
import me.jbusdriver.db.service.HistoryService
import me.jbusdriver.mvp.MovieDetailContract
import me.jbusdriver.mvp.bean.*
import me.jbusdriver.mvp.model.CollectModel
import me.jbusdriver.mvp.model.RecommendModel
import me.jbusdriver.mvp.presenter.MovieDetailPresenterImpl
import me.jbusdriver.ui.holder.*
import org.intellij.lang.annotations.Flow
import java.util.*


class MovieDetailActivity : AppBaseActivity<MovieDetailContract.MovieDetailPresenter, MovieDetailContract.MovieDetailView>(), MovieDetailContract.MovieDetailView {

    private lateinit var collectMenu: MenuItem
    private lateinit var removeCollectMenu: MenuItem

    private val headHolder by lazy { HeaderHolder(this) }
    private val sampleHolder by lazy { ImageSampleHolder(this) }
    private val actressHolder by lazy { ActressListHolder(this) }
    private val genreHolder by lazy { GenresHolder(this) }
    private val relativeMovieHolder by lazy { RelativeMovieHolder(this) }

    override val url by lazy { intent.getStringExtra(C.BundleKey.Key_1) }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        val fab = findViewById<FloatingActionButton>(R.id.fab)
        fab.setOnClickListener {
            if (movie != null) {
                MaterialDialog.Builder(it.context).title("推荐这部影片")
                        .input("说的什么吧！", null, true) { _, str ->
                            KLog.d("input call back : $str")
                            mBasePresenter?.likeIt(movie!!, str.toString())
                        }.positiveText("发送").show()
            }


//            RecommendService.INSTANCE.recommends().compose(SchedulersCompat.io()).subscribe(SimpleSubscriber())
        }
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = movie?.des
        immersionBar.fullScreen(true).transparentStatusBar().titleBar(toolbar).statusBarAlpha(0.12f).init()
        initWidget()
        initData()

    }


    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_movie_detail, menu)
        collectMenu = menu.findItem(R.id.action_add_movie_collect)
        removeCollectMenu = menu.findItem(R.id.action_remove_movie_collect)
        if (movie != null) {
            if (CollectModel.has(movie!!.convertDBItem())) {
                collectMenu.isVisible = false
                removeCollectMenu.isVisible = true
            } else {
                collectMenu.isVisible = true
                removeCollectMenu.isVisible = false
            }
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the CENSORED/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        val id = item.itemId
        when (id) {
            R.id.action_add_movie_collect -> {
                //收藏
                KLog.d("收藏")
                if (movie != null) {
                    if (CollectModel.addToCollect(movie!!.convertDBItem())) {
                        collectMenu.isVisible = false
                        removeCollectMenu.isVisible = true
                    }
                }
            }
            R.id.action_remove_movie_collect -> {
                //取消收藏
                KLog.d("取消收藏")
                if (movie != null) {
                    if (CollectModel.removeCollect(movie!!.convertDBItem())) {
                        collectMenu.isVisible = true
                        removeCollectMenu.isVisible = false
                    }
                }
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun initWidget() {
        sr_refresh.setColorSchemeResources(R.color.colorPrimary, R.color.colorPrimaryDark, R.color.colorPrimaryLight)
        sr_refresh.setOnRefreshListener {
            //reload
            movie?.link?.let {
                //删除缓存和magnet缓存
                CacheLoader.acache.remove(it.urlPath)
                CacheLoader.acache.remove(it.urlPath + "_magnet")
                //重新加载
                mBasePresenter?.loadDetail(it)
                //magnet 不要重新加载
            }

        }
        app_bar.addOnOffsetChangedListener { _, offset ->
            sr_refresh.isEnabled = Math.abs(offset) <= 1
        }

        ll_movie_detail.addView(headHolder.view)
        ll_movie_detail.addView(sampleHolder.view)
        ll_movie_detail.addView(viewContext.inflate(R.layout.layout_load_magnet).apply {
            this.tv_movie_look_magnet.setTextColor(ResourcesCompat.getColor(this@apply.resources, R.color.colorPrimaryDark, null))
            this.tv_movie_look_magnet.paintFlags = this.tv_movie_look_magnet.paintFlags or Paint.UNDERLINE_TEXT_FLAG
            setOnClickListener {
                val code = movie?.code?.replace("-", "") ?: url.urlPath
                MagnetPagerListActivity.start(viewContext, code)
            }
        })
        ll_movie_detail.addView(actressHolder.view)
        ll_movie_detail.addView(genreHolder.view)
        ll_movie_detail.addView(relativeMovieHolder.view)


    }

    private fun initData() {
        (intent.extras?.getSerializable(C.BundleKey.Key_1) as? Movie)?.let {
            movie = it
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        headHolder.release()
        sampleHolder.release()
        actressHolder.release()
        genreHolder.release()
        relativeMovieHolder.release()
        ImmersionBar.with(this).destroy()
    }

    override fun createPresenter() = MovieDetailPresenterImpl(intent?.getBooleanExtra(C.BundleKey.Key_2, false)
            ?: false)

    override val layoutId = R.layout.activity_movie_detail

    override var movie: Movie? = null


    override fun showLoading() {
        KLog.t(TAG).d("showLoading")
        sr_refresh?.let {
            if (!it.isRefreshing) {
                it.post {
                    it.setProgressViewOffset(false, 0, viewContext.dpToPx(24f))
                    it.isRefreshing = true
                }
            }
        } ?: super.showLoading()
    }

    override fun dismissLoading() {
        KLog.t(TAG).d("dismissLoading")
        sr_refresh?.let {
            it.post { it.isRefreshing = false }
        } ?: super.dismissLoading()
    }

    override fun <T> showContent(data: T?) {
        if (data is Movie && movie == null) {
            movie = data
            invalidateOptionsMenu()
        }

        if (data is MovieDetail) {
            //Slide Up Animation
            KLog.d("date : $data")
            movie?.let {
                val likeKey = it.saveKey + "_like"
                Flowable.fromCallable {
                    RecommendModel.getLikeCount(likeKey)
                }.map {
                    Math.min(it, 3)
                }.subscribe {
                    changeLikeIcon(it)
                }.addTo(rxManager)

            }

            supportActionBar?.title = data.title
            //cover fixme
            iv_movie_cover.setOnClickListener { WatchLargeImageActivity.startShow(this, listOf(data.cover) + data.imageSamples.map { it.image }) }
            GlideApp.with(this).load(data.cover.toGlideUrl).thumbnail(0.1f).into(DrawableImageViewTarget(iv_movie_cover))
            //animation
            ll_movie_detail.y = ll_movie_detail.y + 120
            ll_movie_detail.alpha = 0f
            ll_movie_detail.visibility = View.VISIBLE
            ll_movie_detail.animate().translationY(0f).alpha(1f).setDuration(500).start()

            headHolder.init(data.headers)
            sampleHolder.init(data.imageSamples)
            sampleHolder.cover = data.cover
            actressHolder.init(data.actress)
            genreHolder.init(data.genres)
            relativeMovieHolder.init(data.relatedMovies)


        }
    }

    override fun changeLikeIcon(likeCount: Int) {
        KLog.d("changeLikeIcon :$likeCount")
        findViewById<FloatingActionButton>(R.id.fab)?.apply {
            this.setImageDrawable(resources.getDrawable(R.drawable.ic_love_sel))
            DrawableCompat.setTint(this.drawable,
                    ColorUtils.blendARGB(R.color.white.toColorInt(), Color.parseColor("#e91e63"), likeCount / 3f))
        }
    }

    /*===========================other===================================*/
    companion object {
        fun start(current: Context, movie: Movie, fromHistory: Boolean = false) {
            current.startActivity(Intent(current, MovieDetailActivity::class.java).apply {
                putExtra(C.BundleKey.Key_1, movie)
                putExtra(C.BundleKey.Key_2, fromHistory)
            })
        }

        fun start(current: Context, movieUrl: String) {
            current.startActivity(Intent(current, MovieDetailActivity::class.java).apply {
                putExtra(C.BundleKey.Key_1, movieUrl)
                putExtra(C.BundleKey.Key_2, false)
            })
        }

    }

}
