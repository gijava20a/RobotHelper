package com.example.myapplication.activity;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.DTO.Robot;
import com.example.myapplication.R;

import java.util.List;

public class RobotAdapter extends RecyclerView.Adapter<RobotAdapter.RobotViewHolder> {

    private List<Robot> robotList;
    private String page;
    public interface OnRobotClickListener { void onRobotClick(Robot robot); }
    private OnRobotClickListener clickListener;

    public RobotAdapter(List<Robot> robotList, String page) {
        this(robotList, page, null);
    }

    public RobotAdapter(List<Robot> robotList, String page, OnRobotClickListener clickListener) {
        this.robotList = robotList;
        this.page = page;
        this.clickListener = clickListener;
    }

    @NonNull
    @Override
    public RobotViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_robot, parent, false);
        return new RobotViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RobotViewHolder holder, int position) {
        Robot robot = robotList.get(position);
        holder.tvrobotID.setText(robot.getName());
        String status = robot.getStatus();
        int color;
        switch (status.toLowerCase()) {
            case "admin_connected":
                color = Color.parseColor("#FF5252");
                break;
            case "online":
                color = Color.parseColor("#0fdb78");
                break;
            default:
                color = Color.GRAY;
                break;
        }
        holder.viewStatus.setBackgroundColor(color);

        boolean isAvailable = (!page.equals("user") && status.equals("online")) ||
                (page.equals("user") && status.equals("offline"));

        holder.itemView.setAlpha(isAvailable ? 1.0f : 0.5f);
        holder.itemView.setEnabled(isAvailable);
        holder.itemView.setClickable(isAvailable);

        if (isAvailable) {
            holder.itemView.setOnClickListener(v -> {
                if (clickListener != null) {
                    clickListener.onRobotClick(robot);
                } else {
                    Context context = v.getContext();
                    Intent intent = new Intent(context, page.equals("user") ? UserActivity.class : Controller.class);
                    intent.putExtra("id", robot.getId());
                    context.startActivity(intent);
                }
            });
        } else {
            holder.itemView.setOnClickListener(null);
        }
    }

    @Override
    public int getItemCount() {
        return robotList.size();
    }

    public static class RobotViewHolder extends RecyclerView.ViewHolder {
        TextView tvrobotID;
        View viewStatus;

        public RobotViewHolder(@NonNull View itemView) {
            super(itemView);
            tvrobotID = itemView.findViewById(R.id.tvrobotID);
            viewStatus = itemView.findViewById(R.id.viewStatus);
        }
    }
}

