package br.com.okayama.tmanagerci.activities

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.GravityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import br.com.okayama.tmanagerci.R
import br.com.okayama.tmanagerci.activities.adapters.BoardItemsAdapter
import br.com.okayama.tmanagerci.activities.firebase.FirestoreClass
import br.com.okayama.tmanagerci.activities.models.Board
import br.com.okayama.tmanagerci.activities.models.User
import br.com.okayama.tmanagerci.activities.utils.Constants
import br.com.okayama.tmanagerci.databinding.ActivityMainBinding
import br.com.okayama.tmanagerci.databinding.NavHeaderMainBinding
import com.bumptech.glide.Glide
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.iid.internal.FirebaseInstanceIdInternal
import com.google.firebase.messaging.FirebaseMessaging
import java.lang.StringBuilder

class MainActivity : BaseActivity(), NavigationView.OnNavigationItemSelectedListener {

    companion object {
        const val MY_PROFILE_REQUEST_CODE: Int = 11
    }

    private lateinit var mSharedPreferences: SharedPreferences

    private var binding: ActivityMainBinding? = null
    private lateinit var mUserName: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding?.root)
        setupActionBar()
        binding?.navView?.setNavigationItemSelectedListener(this)

        mSharedPreferences =
            this.getSharedPreferences(Constants.PROJECT_PREFERENCES, Context.MODE_PRIVATE)

        val tokenUpdated = mSharedPreferences.getBoolean(Constants.FCM_TOKEN_UPDATED, false)

        if (tokenUpdated) {
            showProgressDialog(resources.getString(R.string.please_wait))
            FirestoreClass().loadUserData(this, true)
        } else {
            FirebaseMessaging.getInstance().token
                .addOnSuccessListener(this@MainActivity) {
                    updateFCMToken(it)
                }
        }

        FirestoreClass().loadUserData(this, true)
        binding?.mainAppBarLayout?.fabCreateBoard?.setOnClickListener {
            val intent = Intent(this, CreateBoardActivity::class.java)
            intent.putExtra(Constants.NAME, mUserName)
            boardLauncher.launch(intent)
        }
    }

    private val boardLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                FirestoreClass().getBoardsList(this)
            }
        }

    private fun setupActionBar() {
        setSupportActionBar(binding?.mainAppBarLayout?.toolbarMainActivity)
        binding?.mainAppBarLayout?.toolbarMainActivity?.setNavigationIcon(R.drawable.ic_action_naviagation_menu)
        binding?.mainAppBarLayout?.toolbarMainActivity?.setNavigationOnClickListener {
            //Toggle drawer
            toggleDrawerLayout()
        }
    }

    private fun toggleDrawerLayout() {
        if (binding?.drawerLayout?.isDrawerOpen(GravityCompat.START) == true) {
            binding?.drawerLayout?.closeDrawer(GravityCompat.START)
        } else {
            binding?.drawerLayout?.openDrawer(GravityCompat.START)
        }
    }

    override fun onBackPressed() {
        if (binding?.drawerLayout?.isDrawerOpen(GravityCompat.START) == true) {
            binding?.drawerLayout?.closeDrawer(GravityCompat.START)
        } else {
            doubleBackToExtit()
        }
    }

    fun updateNavigationUserDetails(user: User, readBoardsList: Boolean) {

        hideProgressDialog()

        mUserName = user.name

        val headerView = binding?.navView?.getHeaderView(0)
        val headerBinding = headerView?.let { NavHeaderMainBinding.bind(it) }

        headerBinding?.navUserImage?.let {
            Glide.with(this).load(user.image).centerCrop()
                .placeholder(R.drawable.ic_user_place_holder).into(it)
        };
        headerBinding?.tvUsername?.text = user.name

        if (readBoardsList) {
            showProgressDialog(resources.getString(R.string.please_wait))
            FirestoreClass().getBoardsList(this)
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_my_profile -> {
                startUpdateActivityAndGetResult.launch(Intent(this, MyProfileActivity::class.java))
            }

            R.id.nav_sign_out -> {
                FirebaseAuth.getInstance().signOut()
                mSharedPreferences.edit().clear().apply()
                val intent = Intent(this, IntroActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                finish()
            }
        }
        binding?.drawerLayout?.closeDrawer(GravityCompat.START)
        return true
    }

    private val startUpdateActivityAndGetResult =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                FirestoreClass().loadUserData(this)
            } else {
                Log.e("onActivityResult()", "Profile update cancelled by user")
            }
        }

    fun populateBoardsListToUi(boardsList: ArrayList<Board>) {
        hideProgressDialog()
        if (boardsList.size > 0) {
            binding?.mainAppBarLayout?.mainContentLayout?.rvBoardsList?.visibility = View.VISIBLE
            binding?.mainAppBarLayout?.mainContentLayout?.tvNoBoardsAvailable?.visibility =
                View.GONE
            binding?.mainAppBarLayout?.mainContentLayout?.rvBoardsList?.layoutManager =
                LinearLayoutManager(this)
            binding?.mainAppBarLayout?.mainContentLayout?.rvBoardsList?.setHasFixedSize(true)

            val adapter = BoardItemsAdapter(this, boardsList)
            binding?.mainAppBarLayout?.mainContentLayout?.rvBoardsList?.adapter = adapter

            adapter.setOnClickListener(object : BoardItemsAdapter.OnClickListener {
                override fun onClick(position: Int, model: Board) {
                    val intent = Intent(this@MainActivity, TaskListActivity::class.java)
                    intent.putExtra(Constants.DOCUMENT_ID, model.documentId)
                    startActivity(intent)
                }
            })
        } else {
            binding?.mainAppBarLayout?.mainContentLayout?.rvBoardsList?.visibility = View.GONE
            binding?.mainAppBarLayout?.mainContentLayout?.tvNoBoardsAvailable?.visibility =
                View.VISIBLE
        }
    }


    fun tokenUpdateSuccess() {
        hideProgressDialog()
        val editor: SharedPreferences.Editor = mSharedPreferences.edit()
        editor.putBoolean(Constants.FCM_TOKEN_UPDATED, true)
        editor.apply()
        showProgressDialog(resources.getString(R.string.please_wait))
        FirestoreClass().loadUserData(this, true)
    }

    private fun updateFCMToken(token: String) {
        val userHashMap = HashMap<String, Any>()
        userHashMap[Constants.FCM_TOKEN] = token
        showProgressDialog(resources.getString(R.string.please_wait))
        FirestoreClass().updateUserProfileData(this, userHashMap)
    }
}