"""
BGMock Digital Twin Simulator
Interactive Streamlit UI for banking system simulation and testing
"""

import streamlit as st
import requests
import os
import time
import json
from datetime import datetime, timedelta
import plotly.graph_objects as go
import plotly.express as px
import pandas as pd
from typing import Dict, List

# Import simulator modules
import sys
sys.path.insert(0, '/app/src')
from simulator.chaos_engine import get_chaos_engine, FailureType
from simulator.load_tester import get_load_tester, LoadProfile

# Configuration
st.set_page_config(
    page_title="BGMock Digital Twin",
    page_icon="🏦",
    layout="wide",
    initial_sidebar_state="expanded"
)

# Custom CSS
st.markdown("""
<style>
    .metric-card {
        background-color: #f0f2f6;
        padding: 20px;
        border-radius: 10px;
        margin: 10px 0;
    }
    .success { color: #28a745; }
    .error { color: #dc3545; }
    .warning { color: #ffc107; }
    }
</style>
""", unsafe_allow_html=True)

def main():
    st.title("🏦 BGMock Banking System - Digital Twin")
    
    # Sidebar
    with st.sidebar:
        st.header("Control Panel")
        
        # Service Health
        st.subheader("🔍 Service Health")
        health = st.session_state.state_manager.service_health
        
        for service, is_healthy in health.items():
            status = "✅" if is_healthy else "❌"
            st.write(f"{status} {service.replace('_', ' ').title()}")
        
        st.divider()
        
        # Transaction Creator
        st.subheader("💸 Create Transaction")
        
        with st.form("create_transaction"):
            from_account = st.text_input("From Account", "1111111111")
            to_bankgood = st.text_input("To Bankgood Number", "2222222222")
            amount = st.number_input("Amount", min_value=1.0, max_value=10000.0, value=100.0)
            use_rest = st.checkbox("Use REST API", value=True)
            
            if st.form_submit_button("Send Transaction"):
                transaction = st.session_state.simulator.create_transaction(
                    from_account=from_account,
                    to_bankgood=to_bankgood,
                    amount=Decimal(str(amount)),
                    use_rest=use_rest
                )
                if transaction:
                    st.success(f"Transaction created: {transaction.transaction_id}")
                else:
                    st.error("Failed to create transaction")
        
        st.divider()
        
        # Simulation Controls
        st.subheader("🎮 Simulation")
        
        col1, col2 = st.columns(2)
        with col1:
            num_transactions = st.number_input("Transactions", min_value=1, max_value=100, value=10)
        with col2:
            delay = st.number_input("Delay (s)", min_value=0.5, max_value=10.0, value=2.0)
        
        if st.button("Run Simulation", use_container_width=True):
            with st.spinner("Running simulation..."):
                st.session_state.simulator.run_simulation(num_transactions, delay)
            st.success("Simulation completed!")
        
        st.divider()
        
        # Auto-refresh
        st.session_state.auto_refresh = st.checkbox(
            "Auto-refresh (5s)",
            value=st.session_state.auto_refresh
        )
        
        if st.session_state.auto_refresh:
            time.sleep(5)
            st.rerun()
    
    # Main content area
    tabs = st.tabs([
        "📊 Dashboard", 
        "💳 Accounts", 
        "📝 Transactions", 
        "🔄 Kafka Events", 
        "🌐 REST Calls",
        "📈 Analytics"
    ])
    
    with tabs[0]:
        render_dashboard()
    
    with tabs[1]:
        render_accounts()
    
    with tabs[2]:
        render_transactions()
    
    with tabs[3]:
        render_kafka_events()
    
    with tabs[4]:
        render_rest_calls()
    
    with tabs[5]:
        render_analytics()

def render_dashboard():
    """Render main dashboard"""
    st.header("System Dashboard")
    
    # Statistics
    stats = st.session_state.state_manager.get_statistics()
    
    col1, col2, col3, col4 = st.columns(4)
    
    with col1:
        st.metric("Total Transactions", stats["total_transactions"])
    
    with col2:
        st.metric("Successful", stats["successful_transactions"], 
                 delta=f"{stats['successful_transactions']/max(stats['total_transactions'], 1)*100:.1f}%")
    
    with col3:
        st.metric("Failed", stats["failed_transactions"])
    
    with col4:
        st.metric("Pending", stats["pending_transactions"])
    
    col1, col2, col3 = st.columns(3)
    
    with col1:
        st.metric("Total Transferred", f"${stats['total_amount_transferred']:.2f}")
    
    with col2:
        st.metric("Kafka Messages", 
                 stats["kafka_messages_sent"] + stats["kafka_messages_received"])
    
    with col3:
        st.metric("REST API Calls", stats["rest_calls_made"])
    
    # Transaction Status Pie Chart
    if stats["total_transactions"] > 0:
        fig = go.Figure(data=[
            go.Pie(
                labels=['Success', 'Failed', 'Pending'],
                values=[
                    stats["successful_transactions"],
                    stats["failed_transactions"], 
                    stats["pending_transactions"]
                ],
                hole=.3
            )
        ])
        fig.update_layout(title="Transaction Status Distribution", height=400)
        st.plotly_chart(fig, use_container_width=True)

def render_accounts():
    """Render accounts view"""
    st.header("Bank Accounts")
    
    accounts = st.session_state.state_manager.get_all_accounts()
    
    if accounts:
        df = pd.DataFrame([{
            "Account ID": acc.account_id[:8] + "...",
            "Account Number": acc.account_number,
            "Account Holder": acc.account_holder,
            "Balance": f"${acc.balance:.2f}"
        } for acc in accounts])
        
        st.dataframe(df, use_container_width=True, hide_index=True)
        
        # Balance distribution chart
        fig = px.bar(
            x=[acc.account_holder for acc in accounts],
            y=[float(acc.balance) for acc in accounts],
            labels={'x': 'Account Holder', 'y': 'Balance ($)'},
            title="Account Balances"
        )
        st.plotly_chart(fig, use_container_width=True)
    else:
        st.info("No accounts found. Initialize test accounts from the simulator.")

def render_transactions():
    """Render transactions view"""
    st.header("Transactions")
    
    transactions = list(st.session_state.state_manager.transactions.values())
    
    if transactions:
        # Sort by created_at descending
        transactions.sort(key=lambda x: x.created_at, reverse=True)
        
        # Create DataFrame
        df = pd.DataFrame([{
            "ID": tx.transaction_id[:8] + "...",
            "From": tx.from_account_number,
            "To": tx.to_bankgood_number,
            "Amount": f"${tx.amount:.2f}",
            "Status": tx.status.value,
            "Created": tx.created_at.strftime("%H:%M:%S"),
            "Updated": tx.updated_at.strftime("%H:%M:%S")
        } for tx in transactions[:50]])  # Show last 50
        
        # Color code by status
        def highlight_status(row):
            if row['Status'] == 'SUCCESS':
                return ['background-color: #d4edda'] * len(row)
            elif row['Status'] == 'FAILED':
                return ['background-color: #f8d7da'] * len(row)
            else:
                return ['background-color: #fff3cd'] * len(row)
        
        styled_df = df.style.apply(highlight_status, axis=1)
        st.dataframe(styled_df, use_container_width=True, hide_index=True)
        
        # Transaction timeline
        fig = go.Figure()
        
        for status in [TransactionStatus.SUCCESS, TransactionStatus.FAILED, TransactionStatus.PENDING]:
            status_txs = [tx for tx in transactions if tx.status == status]
            if status_txs:
                fig.add_trace(go.Scatter(
                    x=[tx.created_at for tx in status_txs],
                    y=[float(tx.amount) for tx in status_txs],
                    mode='markers',
                    name=status.value,
                    marker=dict(size=10)
                ))
        
        fig.update_layout(
            title="Transaction Timeline",
            xaxis_title="Time",
            yaxis_title="Amount ($)",
            height=400
        )
        st.plotly_chart(fig, use_container_width=True)
    else:
        st.info("No transactions yet. Create one from the sidebar.")

def render_kafka_events():
    """Render Kafka events stream"""
    st.header("Kafka Event Stream")
    
    events = st.session_state.state_manager.get_kafka_events(30)
    
    if events:
        # Group by topic
        topics = {}
        for event in events:
            topic = event['topic']
            if topic not in topics:
                topics[topic] = []
            topics[topic].append(event)
        
        # Display events by topic
        for topic, topic_events in topics.items():
            with st.expander(f"📡 {topic} ({len(topic_events)} events)", expanded=True):
                for event in topic_events[-5:]:  # Show last 5
                    timestamp = datetime.fromisoformat(event['timestamp'])
                    st.markdown(f"""
                    <div class="kafka-event">
                        <strong>{timestamp.strftime('%H:%M:%S.%f')[:-3]}</strong> - 
                        {event['type']}
                        <br><small>{str(event['data'])[:100]}...</small>
                    </div>
                    """, unsafe_allow_html=True)
    else:
        st.info("No Kafka events captured yet.")

def render_rest_calls():
    """Render REST API calls"""
    st.header("REST API Calls")
    
    calls = st.session_state.state_manager.get_rest_calls(30)
    
    if calls:
        df = pd.DataFrame(calls)
        df['timestamp'] = pd.to_datetime(df['timestamp'])
        df['time'] = df['timestamp'].dt.strftime('%H:%M:%S')
        
        # Display table
        display_df = df[['time', 'method', 'endpoint', 'status_code', 'response_time']]
        display_df['response_time'] = display_df['response_time'].apply(lambda x: f"{x*1000:.1f}ms")
        
        st.dataframe(display_df, use_container_width=True, hide_index=True)
        
        # Response time chart
        fig = px.line(
            df,
            x='timestamp',
            y='response_time',
            title='API Response Times',
            labels={'response_time': 'Response Time (s)'}
        )
        st.plotly_chart(fig, use_container_width=True)
    else:
        st.info("No REST API calls recorded yet.")

def render_analytics():
    """Render analytics view"""
    st.header("System Analytics")
    
    # Create subplots
    fig = make_subplots(
        rows=2, cols=2,
        subplot_titles=("Message Flow Rate", "Transaction Success Rate", 
                       "System Load", "Amount Distribution"),
        specs=[[{"type": "scatter"}, {"type": "scatter"}],
               [{"type": "indicator"}, {"type": "histogram"}]]
    )
    
    # Mock data for demonstration
    stats = st.session_state.state_manager.get_statistics()
    
    # Message flow (mock time series)
    times = [datetime.now() - timedelta(minutes=i) for i in range(20, 0, -1)]
    messages = [stats["kafka_messages_received"] * (0.8 + i*0.01) for i in range(20)]
    
    fig.add_trace(
        go.Scatter(x=times, y=messages, mode='lines+markers', name='Messages'),
        row=1, col=1
    )
    
    # Success rate
    success_rate = stats["successful_transactions"] / max(stats["total_transactions"], 1) * 100
    fig.add_trace(
        go.Indicator(
            mode="gauge+number",
            value=success_rate,
            title={'text': "Success Rate (%)"},
            gauge={'axis': {'range': [0, 100]},
                  'bar': {'color': "green"},
                  'steps': [
                      {'range': [0, 50], 'color': "lightgray"},
                      {'range': [50, 80], 'color': "yellow"},
                      {'range': [80, 100], 'color': "lightgreen"}],
                  'threshold': {'line': {'color': "red", 'width': 4},
                               'thickness': 0.75, 'value': 95}}
        ),
        row=2, col=1
    )
    
    # Transaction amounts distribution (mock data)
    transactions = list(st.session_state.state_manager.transactions.values())
    if transactions:
        amounts = [float(tx.amount) for tx in transactions]
        fig.add_trace(
            go.Histogram(x=amounts, nbinsx=20, name='Amount'),
            row=2, col=2
        )
    
    fig.update_layout(height=700, showlegend=False)
    st.plotly_chart(fig, use_container_width=True)
    
    # Export state
    if st.button("Export System State"):
        state_json = st.session_state.state_manager.export_state()
        st.download_button(
            label="Download State JSON",
            data=str(state_json),
            file_name=f"bgmock_state_{datetime.now().strftime('%Y%m%d_%H%M%S')}.json",
            mime="application/json"
        )

if __name__ == "__main__":
    main()