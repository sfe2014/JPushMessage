package io.jchat.android.activity;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.widget.Toast;

import cn.jpush.im.android.api.callback.GetGroupInfoCallback;
import cn.jpush.im.android.api.content.EventNotificationContent;
import cn.jpush.im.android.api.content.ImageContent;
import cn.jpush.im.android.api.enums.ContentType;
import cn.jpush.im.android.api.event.ConversationRefreshEvent;
import cn.jpush.im.android.api.event.MessageEvent;
import cn.jpush.im.android.api.model.Conversation;
import cn.jpush.im.android.api.model.GroupInfo;
import cn.jpush.im.android.api.model.Message;
import cn.jpush.im.android.api.model.UserInfo;
import io.jchat.android.R;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.List;

import cn.jpush.im.android.api.JMessageClient;
import cn.jpush.im.android.api.enums.ConversationType;
import io.jchat.android.adapter.MsgListAdapter;
import io.jchat.android.application.JPushDemoApplication;
import io.jchat.android.controller.ChatController;
import io.jchat.android.controller.RecordVoiceBtnController;
import io.jchat.android.tools.BitmapLoader;
import io.jchat.android.view.ChatView;

/*
 * 对话界面
 */
public class ChatActivity extends BaseActivity {

    private static final String TAG = "ChatActivity";

    private ChatView mChatView;
    private ChatController mChatController;
    private GroupNameChangedReceiver mReceiver;
    private String mTargetID;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        // TODO Auto-generated method stub
        super.onCreate(savedInstanceState);
        JMessageClient.registerEventReceiver(this);
        setContentView(R.layout.activity_chat);
        mChatView = (ChatView) findViewById(R.id.chat_view);
        mChatView.initModule();
        mChatController = new ChatController(mChatView, this);
        mChatView.setListeners(mChatController);
        mChatView.setOnTouchListener(mChatController);
        mChatView.setOnScrollListener(mChatController);
        initReceiver();

    }

    // 更新群名变更的广播
    private void initReceiver() {
        mReceiver = new GroupNameChangedReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(JPushDemoApplication.UPDATE_GROUP_NAME_ACTION);
        filter.addAction(Intent.ACTION_HEADSET_PLUG);
        registerReceiver(mReceiver, filter);
    }

    private class GroupNameChangedReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent data) {
            if (data != null) {
                mTargetID = data.getStringExtra("targetID");
                if (data.getAction().equals(
                        JPushDemoApplication.UPDATE_GROUP_NAME_ACTION)) {
                    mChatView.setChatTitle(data.getStringExtra("newGroupName"), mChatController.getGroupMembersCount());
                } else if (data.getAction().equals(Intent.ACTION_HEADSET_PLUG)) {
                    mChatController.getAdapter().setAudioPlayByEarPhone(data.getIntExtra("state", 0));
                }
            }
        }

    }

    /*
    重写BaseActivity的handleMsg()方法，实现刷新消息
     */
    @Override
    public void handleMsg(android.os.Message msg) {
        switch (msg.what) {
            case JPushDemoApplication.UPDATE_CHAT_LIST_VIEW:
                mChatController.getAdapter().refresh();
                break;
            case JPushDemoApplication.REFRESH_GROUP_NAME:
                if (mChatController.getConversation() != null) {
                    int num = msg.getData().getInt("membersCount");
                    mChatView.setChatTitle(mChatController.getConversation().getTitle(), num);
                }
                break;
            case JPushDemoApplication.REFRESH_GROUP_NUM:
                int num = msg.getData().getInt("membersCount");
                mChatView.setChatTitle(ChatActivity.this.getString(R.string.group), num);
                break;
        }
    }

    /**
     * 处理发送图片，刷新界面
     *
     * @param data    intent
     * @param isGroup 是否为群聊
     */
    private void handleImgRefresh(Intent data, boolean isGroup) {
        mTargetID = data.getStringExtra("targetID");
        long groupID = data.getLongExtra("groupID", 0);
        Log.i(TAG, "Refresh Image groupID: " + groupID);
        //判断是否在当前会话中发图片
        if (mTargetID != null) {
            if (mTargetID.equals(mChatController.getTargetID())) {
                // 可能因为从其他界面回到聊天界面时，MsgListAdapter已经收到更新的消息了
                // 但是ListView没有刷新消息，要重新new Adapter, 并把这个Adapter传到ChatController
                // 保证ChatActivity和ChatController使用同一个Adapter
                if (isGroup) {
                    mChatController.setAdapter(new MsgListAdapter(
                            ChatActivity.this, groupID, mChatController.getGroupInfo()));
                } else {
                    mChatController.setAdapter(new MsgListAdapter(ChatActivity.this, mTargetID));
                }

                // 重新绑定Adapter
                mChatView.setChatListAdapter(mChatController.getAdapter());
                mChatController.getAdapter().setSendImg(data.getIntArrayExtra("msgIDs"));
            }
        } else if (groupID != 0) {
            if (groupID == mChatController.getGroupID()) {
                mChatController.setAdapter(new MsgListAdapter(
                        ChatActivity.this, groupID, mChatController.getGroupInfo()));
                // 重新绑定Adapter
                mChatView.setChatListAdapter(mChatController.getAdapter());
                mChatController.getAdapter().setSendImg(data.getIntArrayExtra("msgIDs"));
            }
        }


    }


    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_UP) {
            switch (event.getKeyCode()) {
                case KeyEvent.KEYCODE_BACK:
                    Log.i(TAG, "BACK pressed");
                    if (RecordVoiceBtnController.mIsPressed) {
                        mChatView.dismissRecordDialog();
                        mChatView.releaseRecorder();
                        RecordVoiceBtnController.mIsPressed = false;
                    }
                    if (mChatController.mIsShowMoreMenu) {
                        mChatView.resetMoreMenuHeight();
                        mChatView.dismissMoreMenu();
                        mChatController.dismissSoftInput();
                        ChatController.mIsShowMoreMenu = false;
                        //清空未读数
                    } else {
                        if (mChatController.isGroup()) {
                            long groupID = mChatController.getGroupID();
                            Log.i(TAG, "groupID " + groupID);
                            Conversation conv = JMessageClient.getGroupConversation(groupID);
                            conv.resetUnreadCount();
                        } else {
                            mTargetID = mChatController.getTargetID();
                            Conversation conv = JMessageClient.getSingleConversation(mTargetID);
                            conv.resetUnreadCount();
                        }
                    }
                    break;
                case KeyEvent.KEYCODE_MENU:
                    // 处理自己的逻辑
                    break;
                case KeyEvent.KEYCODE_ESCAPE:
                    Log.i(TAG, "KeyCode: escape");
                    break;
                default:
                    break;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    /**
     * 释放资源
     */
    @Override
    protected void onDestroy() {
        // TODO Auto-generated method stub
        JMessageClient.unRegisterEventReceiver(this);
        super.onDestroy();
        unregisterReceiver(mReceiver);
        mChatController.releaseMediaPlayer();
        mChatView.releaseRecorder();
    }

    @Override
    protected void onPause() {
        RecordVoiceBtnController.mIsPressed = false;
        JMessageClient.exitConversaion();
        Log.i(TAG, "[Life cycle] - onPause");
        super.onPause();
    }

    @Override
    protected void onStop() {
        mChatController.getAdapter().stopMediaPlayer();
        if (mChatController.mIsShowMoreMenu) {
            mChatView.dismissMoreMenu();
            mChatController.dismissSoftInput();
            ChatController.mIsShowMoreMenu = false;
        }
        if (mChatController.getConversation() != null)
            mChatController.getConversation().resetUnreadCount();
        Log.i(TAG, "[Life cycle] - onStop");
        super.onStop();
    }

    @Override
    protected void onResume() {
        if (!RecordVoiceBtnController.mIsPressed)
            mChatView.dismissRecordDialog();
        String targetID = getIntent().getStringExtra("targetID");
        boolean isGroup = getIntent().getBooleanExtra("isGroup", false);
        if (isGroup) {
            try {
                long groupID = getIntent().getLongExtra("groupID", 0);
                if (groupID == 0){
                    JMessageClient.enterGroupConversation(Long.parseLong(targetID));
                }else JMessageClient.enterGroupConversation(groupID);
            } catch (NumberFormatException nfe) {
                nfe.printStackTrace();
            }
        } else if (null != targetID) {
            JMessageClient.enterSingleConversaion(targetID);
        }

        boolean sendPicture = getIntent().getBooleanExtra("sendPicture", false);
        if (sendPicture) {
            handleImgRefresh(getIntent(), isGroup);
            getIntent().putExtra("sendPicture", false);
        }
        mChatController.refresh();
        mChatController.getAdapter().initMediaPlayer();
        Log.i(TAG, "[Life cycle] - onResume");
        super.onResume();
    }

    /**
     * 用于处理拍照发送图片返回结果
     *
     * @param requestCode 请求码
     * @param resultCode  返回码
     * @param data        intent
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == Activity.RESULT_CANCELED) {
            return;
        }
        if (requestCode == JPushDemoApplication.REQUESTCODE_TAKE_PHOTO) {
            Conversation conv = mChatController.getConversation();
            try {
                String originPath = mChatController.getPhotoPath();
                Bitmap bitmap = BitmapLoader.getBitmapFromFile(originPath, 720, 1280);
                String thumbnailPath = BitmapLoader.saveBitmapToLocal(bitmap);
                File file = new File(thumbnailPath);
                ImageContent content = new ImageContent(file);
                Message msg = conv.createSendMessage(content);
                boolean isGroup = getIntent().getBooleanExtra("isGroup", false);
                Intent intent = new Intent();
                intent.putExtra("msgIDs", new int[]{msg.getId()});
                if (conv.getType() == ConversationType.group) {
                    intent.putExtra("groupID", Long.parseLong(conv.getTargetId()));
                } else {
                    intent.putExtra("targetID", msg.getTargetID());
                }
                handleImgRefresh(intent, isGroup);
//                mChatController.refresh();
            } catch (FileNotFoundException e) {
                Log.i(TAG, "create file failed!");
            } catch (NullPointerException e) {
                Log.i(TAG, "onActivityResult unexpected result");
            }
        }
    }

    public void StartChatDetailActivity(boolean isGroup, String targetID, long groupID) {
        Intent intent = new Intent();
        intent.putExtra("isGroup", isGroup);
        intent.putExtra("targetID", targetID);
        intent.putExtra("groupID", groupID);
        intent.setClass(this, ChatDetailActivity.class);
        startActivity(intent);
    }

    public void StartPickPictureTotalActivity(Intent intent) {
        if (!Environment.getExternalStorageState().equals(
                Environment.MEDIA_MOUNTED)) {
            Toast.makeText(this, this.getString(R.string.sdcard_not_exist_toast), Toast.LENGTH_SHORT).show();
        } else {
            intent.setClass(this, PickPictureTotalActivity.class);
            startActivity(intent);
        }
    }

    public void onEvent(ConversationRefreshEvent conversationRefreshEvent) {
        mHandler.sendEmptyMessage(JPushDemoApplication.REFRESH_GROUP_NAME);
    }

    /**
     * 接收消息类事件
     *
     * @param event 消息事件
     */
    public void onEvent(MessageEvent event) {
        Message msg = event.getMessage();
        //若为群聊相关事件，如添加、删除群成员
        Log.i(TAG, event.getMessage().toString());
        if (msg.getContentType() == ContentType.eventNotification) {
            long groupID = Long.parseLong(event.getMessage().getTargetID());
            UserInfo myInfo = JMessageClient.getMyInfo();
            EventNotificationContent.EventNotificationType type = ((EventNotificationContent) msg.getContent()).getEventNotificationType();
            if (type.equals(EventNotificationContent.EventNotificationType.group_member_removed)) {
                //删除群成员事件
                List<String> userNames = ((EventNotificationContent) msg.getContent()).getUserNames();
                //群主删除了当前用户，则隐藏聊天详情按钮
                if (groupID == mChatController.getGroupID()) {
                    refreshGroupNum();
                    if (userNames.contains(myInfo.getNickname()) || userNames.contains(myInfo.getUserName())){
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                mChatView.dismissRightBtn();
                            }
                        });
                    }
                }
            } else {
                //添加群成员事件
                List<String> userNames = ((EventNotificationContent) msg.getContent()).getUserNames();
                //群主把当前用户添加到群聊，则显示聊天详情按钮
                if (groupID == mChatController.getGroupID()) {
                    refreshGroupNum();
                    if (userNames.contains(myInfo.getNickname()) || userNames.contains(myInfo.getUserName())){
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                mChatView.showRightBtn();
                            }
                        });
                    }
                }
            }
        }
        //刷新消息
        mHandler.sendEmptyMessage(JPushDemoApplication.UPDATE_CHAT_LIST_VIEW);
    }

    private void refreshGroupNum() {
        JMessageClient.getGroupInfo(mChatController.getGroupID(), new GetGroupInfoCallback() {
            @Override
            public void gotResult(int status, String desc, GroupInfo groupInfo) {
                if (status == 0) {
                    if (!TextUtils.isEmpty(groupInfo.getGroupName())) {
                        mChatController.refreshGroupInfo(groupInfo);
                        android.os.Message handleMessage = mHandler.obtainMessage();
                        handleMessage.what = JPushDemoApplication.REFRESH_GROUP_NAME;
                        Bundle bundle = new Bundle();
                        bundle.putInt("membersCount", groupInfo.getGroupMembers().size());
                        handleMessage.setData(bundle);
                        handleMessage.sendToTarget();
                    } else {
                        android.os.Message handleMessage = mHandler.obtainMessage();
                        handleMessage.what = JPushDemoApplication.REFRESH_GROUP_NUM;
                        Bundle bundle = new Bundle();
                        bundle.putInt("membersCount", groupInfo.getGroupMembers().size());
                        handleMessage.setData(bundle);
                        handleMessage.sendToTarget();
                    }
                }
            }
        });
    }
}
