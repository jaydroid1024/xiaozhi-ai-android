package com.xiaozhi.ai.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.xiaozhi.ai.R
import com.xiaozhi.ai.data.Message
import com.xiaozhi.ai.data.MessageRole
import java.text.SimpleDateFormat
import java.util.*

/**
 * 消息列表适配器
 */
class MessageAdapter(private val messages: MutableList<Message> = mutableListOf()) :
    RecyclerView.Adapter<MessageAdapter.MessageViewHolder>() {

    class MessageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val messageContainer: ViewGroup = view.findViewById(R.id.message_container)
        val aiAvatarContainer: FrameLayout = view.findViewById(R.id.ai_avatar_container)
        val userAvatarContainer: FrameLayout = view.findViewById(R.id.user_avatar_container)
        val messageBubble: FrameLayout = view.findViewById(R.id.message_bubble)
        val messageText: TextView = view.findViewById(R.id.message_text)
        val messageTimestamp: TextView = view.findViewById(R.id.message_timestamp)
        val bubbleContainer: ViewGroup = view.findViewById(R.id.message_bubble_container)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_message, parent, false)
        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val message = messages[position]
        val isUser = message.role == MessageRole.USER
        val isAssistant = message.role == MessageRole.ASSISTANT

        // 设置消息文本
        holder.messageText.text = message.content

        // 设置时间戳
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        holder.messageTimestamp.text = timeFormat.format(Date(message.timestamp))

        // 根据消息类型调整布局
        if (isUser) {
            // 用户消息 - 头像在右侧
            holder.aiAvatarContainer.visibility = View.GONE
            holder.userAvatarContainer.visibility = View.VISIBLE
//            holder.messageContainer.gravity = android.view.Gravity.END

            // 调整消息气泡背景
            holder.messageBubble.setBackgroundResource(R.drawable.bg_message_bubble_user)

            // 设置文本颜色
            holder.messageText.setTextColor(holder.itemView.context.getColor(R.color.onPrimary))

            // 调整时间戳位置和颜色
            val layoutParams = holder.messageTimestamp.layoutParams as ViewGroup.MarginLayoutParams
            layoutParams.setMargins(0, layoutParams.topMargin, 8, layoutParams.bottomMargin)
            holder.messageTimestamp.layoutParams = layoutParams
            holder.messageTimestamp.setTextColor(holder.itemView.context.getColor(R.color.onSurfaceVariant))
        } else if (isAssistant) {
            // AI消息 - 头像在左侧
            holder.aiAvatarContainer.visibility = View.VISIBLE
            holder.userAvatarContainer.visibility = View.GONE
//            holder.messageContainer.gravity = android.view.Gravity.START

            // 调整消息气泡背景
            holder.messageBubble.setBackgroundResource(R.drawable.bg_message_bubble)

            // 设置文本颜色
            holder.messageText.setTextColor(holder.itemView.context.getColor(R.color.onSurfaceVariant))

            // 调整时间戳位置和颜色
            val layoutParams = holder.messageTimestamp.layoutParams as ViewGroup.MarginLayoutParams
            layoutParams.setMargins(8, layoutParams.topMargin, 0, layoutParams.bottomMargin)
            holder.messageTimestamp.layoutParams = layoutParams
            holder.messageTimestamp.setTextColor(holder.itemView.context.getColor(R.color.onSurfaceVariant))
        } else {
            // 系统消息 - 居中显示
            holder.aiAvatarContainer.visibility = View.GONE
            holder.userAvatarContainer.visibility = View.GONE
//            holder.messageContainer.gravity = android.view.Gravity.CENTER

            // 调整消息气泡背景
            holder.messageBubble.setBackgroundResource(R.drawable.bg_message_bubble_system)

            // 设置文本颜色
            holder.messageText.setTextColor(holder.itemView.context.getColor(R.color.onSurfaceVariant))

            // 调整时间戳位置和颜色
            val layoutParams = holder.messageTimestamp.layoutParams as ViewGroup.MarginLayoutParams
            layoutParams.setMargins(0, layoutParams.topMargin, 0, layoutParams.bottomMargin)
            holder.messageTimestamp.layoutParams = layoutParams
            holder.messageTimestamp.setTextColor(holder.itemView.context.getColor(R.color.onSurfaceVariant))
        }
    }

    override fun getItemCount() = messages.size

    fun updateMessages(newMessages: List<Message>) {
        messages.clear()
        messages.addAll(newMessages)
        notifyDataSetChanged()
    }

    fun addMessage(message: Message) {
        messages.add(message)
        notifyItemInserted(messages.size - 1)
    }
}