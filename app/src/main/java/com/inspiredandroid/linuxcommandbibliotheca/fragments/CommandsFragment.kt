package com.inspiredandroid.linuxcommandbibliotheca.fragments

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.*
import androidx.appcompat.widget.SearchView
import com.inspiredandroid.linuxcommandbibliotheca.R
import com.inspiredandroid.linuxcommandbibliotheca.activities.AboutActivity
import com.inspiredandroid.linuxcommandbibliotheca.adapter.CommandsAdapter
import com.inspiredandroid.linuxcommandbibliotheca.fragments.dialogs.NewsDialogFragment
import com.inspiredandroid.linuxcommandbibliotheca.fragments.dialogs.RateDialogFragment
import com.inspiredandroid.linuxcommandbibliotheca.interfaces.OnClickListListener
import com.inspiredandroid.linuxcommandbibliotheca.misc.AppManager
import com.inspiredandroid.linuxcommandbibliotheca.misc.FragmentCoordinator
import com.inspiredandroid.linuxcommandbibliotheca.models.Command
import io.realm.RealmResults
import io.realm.kotlin.where
import kotlinx.android.synthetic.main.fragment_commands.*
import java.text.Normalizer
import java.util.*

/* Copyright 2019 Simon Schubert
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
*/
class CommandsFragment : BaseFragment(), OnClickListListener {

    private lateinit var adapter: CommandsAdapter
    private var searchQuery = ""

    /**
     * Get list of all commands sorted by name
     *
     * @return
     */
    private val allCommands: List<RealmResults<Command>>
        get() {
            val results = ArrayList<RealmResults<Command>>()
            val ids = AppManager.getBookmarkIds(context)
            results.add(realm.where<Command>().`in`(Command.ID, ids.toTypedArray()).findAll())
            results.add(realm.where<Command>().findAll().sort("name"))
            return results
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setHasOptionsMenu(true)

        activity?.title = getString(R.string.fragment_bibliotheca_commands)

        adapter = CommandsAdapter(context, allCommands)
        adapter.setOnListClickListener(this)

        if (AppManager.shouldShowNewsDialog(context)) {
            val newDialogFragment = NewsDialogFragment.instance
            newDialogFragment.show(childFragmentManager, newDialogFragment.javaClass.canonicalName)
        } else if (AppManager.shouldShowRateDialog(context)) {
            val rateDialogFragment = RateDialogFragment.instance
            rateDialogFragment.show(childFragmentManager, RateDialogFragment::class.java.name)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_commands, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView.adapter = adapter
        fastScroller.setRecyclerView(recyclerView)

        btnRequestCommand.setOnClickListener {
            sendCommandRequestEmail()
        }
    }

    override fun onClickList(id: Int) {
        FragmentCoordinator.startCommandManActivity(activity, id.toLong())
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.search_about, menu)

        val item = menu.findItem(R.id.search)
        val searchView = item?.actionView as SearchView

        activity?.let {
            val searchManager = it.getSystemService(Context.SEARCH_SERVICE) as SearchManager
            searchView.setSearchableInfo(searchManager.getSearchableInfo(it.componentName))

            searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {

                override fun onQueryTextSubmit(s: String): Boolean {
                    return false
                }

                override fun onQueryTextChange(query: String): Boolean {
                    if (!isAdded) {
                        return true
                    }
                    searchQuery = query
                    if (query.isNotEmpty()) {
                        val normalizedText = Normalizer.normalize(query, Normalizer.Form.NFD).replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "").toLowerCase()
                        search(normalizedText)
                    } else {
                        resetSearchResults()
                    }

                    return true
                }
            })
            item.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
                override fun onMenuItemActionExpand(item: MenuItem): Boolean {
                    return true
                }

                override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                    resetSearchResults()
                    return true
                }
            })
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.about) {
            startAboutActivity()
            return true
        }
        return false
    }

    override fun onResume() {
        super.onResume()

        if (AppManager.hasBookmarkChanged(context)) {
            resetSearchResults()
        }
    }

    private fun startAboutActivity() {
        val intent = Intent(context, AboutActivity::class.java)
        startActivity(intent)
    }

    private fun sendCommandRequestEmail() {
        val intent = Intent(Intent.ACTION_SENDTO)
        intent.data = Uri.parse("mailto:" + "sschubert89@gmail.com")
        intent.putExtra(Intent.EXTRA_SUBJECT, "Command request")
        intent.putExtra(Intent.EXTRA_TEXT, "Command: $searchQuery")
        try {
            startActivity(Intent.createChooser(intent, "Send mail..."))
        } catch (ignored: android.content.ActivityNotFoundException) {
        }
    }

    /**
     * reset adapter entries
     */
    private fun resetSearchResults() {
        adapter.updateRealmResults(allCommands)
        adapter.setSearchQuery("")
        adapter.updateBookmarkIds(context)
        updateViews()
    }

    /**
     * search for query in all command names and short descriptions and update adapter
     *
     * @param query search query
     */
    private fun search(query: String) {
        val results = ArrayList<RealmResults<Command>>()
        results.add(realm.where<Command>().equalTo(Command.NAME, query).findAll())
        results.add(realm.where<Command>().beginsWith(Command.NAME, query).notEqualTo(Command.NAME, query).findAll())
        results.add(realm.where<Command>().contains(Command.NAME, query).not().beginsWith(Command.NAME, query).notEqualTo(Command.NAME, query).findAll())
        results.add(realm.where<Command>().contains(Command.DESCRIPTION, query).not().contains(Command.NAME, query).findAll())

        adapter.updateRealmResults(results)
        adapter.setSearchQuery(query)

        updateViews()
    }

    private fun updateViews() {
        if (adapter.itemCount == 0) {
            btnRequestCommand.visibility = View.VISIBLE
            tvRequestInfo.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            btnRequestCommand.visibility = View.GONE
            tvRequestInfo.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
        }
    }
}
