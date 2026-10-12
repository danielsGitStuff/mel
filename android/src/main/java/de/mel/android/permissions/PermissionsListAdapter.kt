package de.mel.android.permissions

import android.content.Context
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.TextView
import androidx.core.view.setPadding
import de.mel.AndroidPermission
import de.mel.R

// todo clean
class PermissionsListAdapter(
    context: Context,
    val permissions: List<AndroidPermission>,
    private val permissionListAdapterClickListener: (AndroidPermission) -> Unit
) :
    BaseAdapter() {
    private val layoutInflater =
        context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater

    private val padding = context.resources.getDimension(R.dimen.text_margin).toInt()


    override fun getCount() = permissions.size
    override fun getItem(position: Int) = permissions[position]

    override fun getItemId(position: Int) = position.toLong()

    override fun getView(position: Int, oldeView: View?, parent: ViewGroup?): View {
        val permission = permissions[position]
        val view = oldeView ?: layoutInflater.inflate(R.layout.listitem_permission_grant, null)
//        val view = layoutInflater.inflate(R.layout.listitem_permission_grant, null)
        val textTitle = view.findViewById<TextView>(R.id.textTitle)
        textTitle.setText(permission.titleInt)
        textTitle.setTypeface(textTitle.typeface, Typeface.BOLD)
        textTitle.setPadding(padding, padding, padding, 0)
        val textText = view.findViewById<TextView>(R.id.textText)
        textText.setText(permission.textInt)
        textText.setPadding(padding, 0, padding, padding)
        val btn = view.findViewById<Button>(R.id.btnGrant)
        btn.setOnClickListener { permissionListAdapterClickListener(permission) }
        return view
    }

}