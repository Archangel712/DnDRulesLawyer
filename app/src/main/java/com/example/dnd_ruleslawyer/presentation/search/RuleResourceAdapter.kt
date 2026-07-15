package com.example.dnd_ruleslawyer.presentation.search

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.dnd_ruleslawyer.R
import com.example.dnd_ruleslawyer.databinding.ItemRuleResourceBinding
import com.example.dnd_ruleslawyer.domain.model.RuleResource
import coil3.load
import coil3.request.error
import coil3.request.placeholder

class RuleResourceAdapter(
    private val onRuleClicked: (RuleResource) -> Unit,
    private val onFavoriteClicked: ((RuleResource) -> Unit)? = null
) : ListAdapter<RuleResource, RuleResourceAdapter.RuleResourceViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RuleResourceViewHolder {
        val binding = ItemRuleResourceBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return RuleResourceViewHolder(binding, onRuleClicked, onFavoriteClicked)
    }

    override fun onBindViewHolder(holder: RuleResourceViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class RuleResourceViewHolder(
        private val binding: ItemRuleResourceBinding,
        private val onRuleClicked: (RuleResource) -> Unit,
        private val onFavoriteClicked: ((RuleResource) -> Unit)?
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(rule: RuleResource) {
            binding.ruleNameText.text = rule.name
            binding.ruleMetaText.text = binding.root.context.getString(
                R.string.rule_result_meta,
                rule.type.displayName(),
                rule.source.displayName()
            )
            if (rule.description.isBlank()) {
                binding.ruleDescriptionText.visibility = View.GONE
                binding.ruleDescriptionText.text = ""
            } else {
                binding.ruleDescriptionText.visibility = View.VISIBLE
                binding.ruleDescriptionText.text = rule.description
            }
            val placeholder = rule.type.placeholderDrawableRes

            if (rule.imageUrl.isNullOrBlank()) {
                binding.ruleIconImage.setImageResource(placeholder)
            } else {
                binding.ruleIconImage.load(rule.imageUrl) {
                    placeholder(placeholder)
                    error(placeholder)
                }
            }
            binding.root.setOnClickListener {
                onRuleClicked(rule)
            }
            binding.favoriteButton.setIconResource(
                if (rule.isFavorite) R.drawable.ic_star_24 else R.drawable.ic_star_border_24
            )
            binding.favoriteButton.contentDescription = binding.root.context.getString(
                if (rule.isFavorite) R.string.favorite_remove else R.string.favorite_add
            )
            binding.favoriteButton.setOnClickListener {
                onFavoriteClicked?.invoke(rule)
            }
            binding.favoriteButton.isEnabled = onFavoriteClicked != null
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
