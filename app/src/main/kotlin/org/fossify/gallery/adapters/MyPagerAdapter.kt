package org.fossify.gallery.adapters

import android.os.Bundle
import android.os.Parcelable
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentStatePagerAdapter
import androidx.viewpager.widget.PagerAdapter
import org.fossify.gallery.activities.ViewPagerActivity
import org.fossify.gallery.fragments.PhotoFragment
import org.fossify.gallery.fragments.VideoFragment
import org.fossify.gallery.fragments.ViewPagerFragment
import org.fossify.gallery.helpers.MEDIUM
import org.fossify.gallery.helpers.SHOULD_INIT_FRAGMENT
import org.fossify.gallery.models.Medium

class MyPagerAdapter(val activity: ViewPagerActivity, fm: FragmentManager, val media: MutableList<Medium>) : FragmentStatePagerAdapter(fm) {
    private val fragments = HashMap<Int, ViewPagerFragment>()
    var shouldInitFragment = true
    private var preservedFragment: ViewPagerFragment? = null
    private var preservedNewPosition: Int = PagerAdapter.POSITION_NONE

    override fun getCount() = media.size

    override fun getItem(position: Int): Fragment {
        val medium = media[position]
        val bundle = Bundle()
        bundle.putSerializable(MEDIUM, medium)
        bundle.putBoolean(SHOULD_INIT_FRAGMENT, shouldInitFragment)
        val fragment = if (medium.isVideo()) {
            VideoFragment()
        } else {
            PhotoFragment()
        }

        fragment.arguments = bundle
        fragment.listener = activity
        return fragment
    }

    override fun getItemPosition(item: Any): Int {
        if (preservedFragment != null && item === preservedFragment) {
            return preservedNewPosition
        }
        return PagerAdapter.POSITION_NONE
    }

    override fun instantiateItem(container: ViewGroup, position: Int): Any {
        val fragment = super.instantiateItem(container, position) as ViewPagerFragment

        // getItem() might not be called if the activity is recreated, so the listener must be set here
        fragment.listener = activity

        fragments[position] = fragment
        return fragment
    }

    override fun destroyItem(container: ViewGroup, position: Int, any: Any) {
        if (fragments[position] === any) {
            fragments.remove(position)
        }
        super.destroyItem(container, position, any)
    }

    fun syncMediaMetadata(newMedia: List<Medium>) {
        if (newMedia.size != media.size) {
            return
        }
        for (i in newMedia.indices) {
            if (media[i].path != newMedia[i].path) {
                return
            }
        }
        for (i in newMedia.indices) {
            media[i] = newMedia[i]
        }
    }

    /**
     * Updates the backing media list on shuffle while keeping the currently visible fragment
     * alive (so an active video continues playing without recreating ExoPlayer), only replacing
     * the offscreen neighbour fragments.
     */
    @Suppress("UNCHECKED_CAST")
    fun updateItemsKeepingActiveFragment(
        newMedia: List<Medium>,
        activeOldPos: Int,
        activeNewPos: Int
    ): Boolean {
        val activeFragment = fragments[activeOldPos] ?: return false
        return try {
            val mFragmentsField = FragmentStatePagerAdapter::class.java.getDeclaredField("mFragments").apply {
                isAccessible = true
            }
            val internalFragments = mFragmentsField.get(this) as? ArrayList<Fragment?> ?: return false

            if (activeOldPos in 0 until internalFragments.size) {
                internalFragments[activeOldPos] = null
            }
            fragments.remove(activeOldPos)

            // Clear stale saved states so new items at old positions do not inherit unrelated state.
            runCatching {
                val mSavedStateField = FragmentStatePagerAdapter::class.java.getDeclaredField("mSavedState").apply {
                    isAccessible = true
                }
                (mSavedStateField.get(this) as? ArrayList<*>)?.clear()
            }

            media.clear()
            media.addAll(newMedia)

            preservedFragment = activeFragment
            preservedNewPosition = activeNewPos
            try {
                notifyDataSetChanged()
            } finally {
                preservedFragment = null
                preservedNewPosition = PagerAdapter.POSITION_NONE
            }

            while (internalFragments.size <= activeNewPos) {
                internalFragments.add(null)
            }
            internalFragments[activeNewPos] = activeFragment
            activeFragment.listener = activity
            fragments[activeNewPos] = activeFragment
            true
        } catch (_: Throwable) {
            false
        }
    }

    fun getCurrentFragment(position: Int) = fragments[position]

    fun toggleFullscreen(isFullscreen: Boolean) {
        for ((pos, fragment) in fragments) {
            fragment.fullscreenToggled(isFullscreen)
        }
    }

    fun onPageScrollStateChanged(state: Int) {
        for (fragment in fragments.values.toList()) {
            fragment.onPageScrollStateChanged(state)
        }
    }

    // try fixing TransactionTooLargeException crash on Android Nougat, tip from https://stackoverflow.com/a/43193425/1967672
    override fun saveState(): Parcelable? {
        val bundle = super.saveState() as Bundle?
        bundle?.putParcelableArray("states", null)
        return bundle
    }
}
