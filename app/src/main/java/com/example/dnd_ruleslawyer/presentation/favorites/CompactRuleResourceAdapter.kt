package com.example.dnd_ruleslawyer.presentation.favorites

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.dnd_ruleslawyer.R
import com.example.dnd_ruleslawyer.databinding.ItemCompactRuleResourceBinding
import com.example.dnd_ruleslawyer.domain.model.RuleResource
import com.example.dnd_ruleslawyer.presentation.search.displayName

class CompactRuleResourceAdapter(
    @param:DrawableRes private val actionIconResId: Int,
    @param:StringRes private val actionContentDescriptionResId: Int,
    private val onRuleClicked: ((RuleResource) -> Unit)? = null,
    private val onActionClicked: (RuleResource) -> Unit
) : ListAdapter<RuleResource, CompactRuleResourceAdapter.CompactRuleResourceViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CompactRuleResourceViewHolder {
        val binding = ItemCompactRuleResourceBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return CompactRuleResourceViewHolder(
            binding = binding,
            actionIconResId = actionIconResId,
            actionContentDescriptionResId = actionContentDescriptionResId,
            onRuleClicked = onRuleClicked,
            onActionClicked = onActionClicked
        )
    }

    override fun onBindViewHolder(holder: CompactRuleResourceViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class CompactRuleResourceViewHolder(
        private val binding: ItemCompactRuleResourceBinding,
        @param:DrawableRes private val actionIconResId: Int,
        @param:StringRes private val actionContentDescriptionResId: Int,
        private val onRuleClicked: ((RuleResource) -> Unit)?,
        private val onActionClicked: (RuleResource) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(rule: RuleResource) {
            binding.resourceNameText.text = rule.name
            binding.resourceMetaText.text = binding.root.context.getString(
                R.string.rule_result_meta,
                rule.type.displayName(),
                rule.source.displayName()
            )
            binding.resourceActionButton.setIconResource(actionIconResId)
            binding.resourceActionButton.contentDescription =
                binding.root.context.getString(actionContentDescriptionResId)
            binding.resourceActionButton.setOnClickListener {
                onActionClicked(rule)
            }
            binding.root.setOnClickListener {
                onRuleClicked?.invoke(rule)
            }
            binding.root.isClickable = onRuleClicked != null
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<RuleResource>() {
        override fun areItemsTheSame(oldItem: RuleResource, newItem: RuleResource): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: RuleResource, newItem: RuleResource): Boolean {
            return oldItem == newItem
        }
    }
}
