package de.mel.android.permissions

import android.content.Context
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import de.mel.AndroidPermission
import de.mel.R

class PermissionsListAdapter(
    private val context: Context,
    private val permissions: List<AndroidPermission>,
    private val permissionListAdapterClickListener: (AndroidPermission) -> Unit
) : RecyclerView.Adapter<PermissionsListAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textTitle: TextView = view.findViewById(R.id.textTitle)
        val textText: TextView = view.findViewById(R.id.textText)
        val btnGrant: Button = view.findViewById(R.id.btnGrant)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.listitem_permission_grant, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val permission = permissions[position]

        if (permission.titleInt != 0) {
            holder.textTitle.setText(permission.titleInt)
        } else {
            holder.textTitle.text = permission.permission
        }
        holder.textTitle.setTypeface(holder.textTitle.typeface, Typeface.BOLD)

        if (permission.textInt != 0) {
            holder.textText.setText(permission.textInt)
        } else {
            holder.textText.text = ""
        }

        holder.btnGrant.setOnClickListener {
            permissionListAdapterClickListener(permission)
        }
    }

    override fun getItemCount(): Int = permissions.size
}