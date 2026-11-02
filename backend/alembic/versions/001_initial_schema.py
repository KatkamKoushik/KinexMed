"""001_initial_schema

Revision ID: 001_initial_schema
Revises: 
Create Date: 2026-11-02 18:40:00.000000

"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa


# revision identifiers, used by Alembic.
revision: str = '001_initial_schema'
down_revision: Union[str, None] = None
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    # 1. sessions
    op.create_table(
        'sessions',
        sa.Column('id', sa.String(length=64), primary_key=True, nullable=False),
        sa.Column('device_id', sa.String(length=64), nullable=True),
        sa.Column('exercise_name', sa.String(length=32), nullable=True),
        sa.Column('started_at', sa.BigInteger(), nullable=False),
        sa.Column('completed_at', sa.BigInteger(), nullable=False),
        sa.Column('duration_seconds', sa.Float(), nullable=False),
        sa.Column('total_reps', sa.Integer(), nullable=False),
        sa.Column('valid_reps', sa.Integer(), nullable=False),
        sa.Column('avg_peak_knee_angle', sa.Float(), nullable=True),
        sa.Column('min_knee_angle', sa.Float(), nullable=True),
        sa.Column('max_knee_angle', sa.Float(), nullable=True),
        sa.Column('evidence_failure_count', sa.Integer(), nullable=True),
        sa.Column('created_at', sa.DateTime(), nullable=True),
    )
    op.create_index(op.f('ix_sessions_id'), 'sessions', ['id'], unique=False)
    op.create_index(op.f('ix_sessions_device_id'), 'sessions', ['device_id'], unique=False)
    op.create_index(op.f('ix_sessions_exercise_name'), 'sessions', ['exercise_name'], unique=False)
    op.create_index(op.f('ix_sessions_created_at'), 'sessions', ['created_at'], unique=False)
    op.create_index('ix_sessions_device_started', 'sessions', ['device_id', 'started_at'], unique=False)
    op.create_index('ix_sessions_exercise_created', 'sessions', ['exercise_name', 'created_at'], unique=False)

    # 2. session_reps
    op.create_table(
        'session_reps',
        sa.Column('id', sa.String(length=64), primary_key=True, nullable=False),
        sa.Column('session_id', sa.String(length=64), sa.ForeignKey('sessions.id', ondelete='CASCADE'), nullable=False),
        sa.Column('rep_number', sa.Integer(), nullable=False),
        sa.Column('is_valid', sa.Boolean(), nullable=False),
        sa.Column('start_timestamp_ms', sa.BigInteger(), nullable=False),
        sa.Column('peak_timestamp_ms', sa.BigInteger(), nullable=False),
        sa.Column('end_timestamp_ms', sa.BigInteger(), nullable=False),
        sa.Column('duration_ms', sa.BigInteger(), nullable=False),
        sa.Column('peak_knee_angle', sa.Float(), nullable=False),
        sa.Column('start_knee_angle', sa.Float(), nullable=False),
        sa.Column('end_knee_angle', sa.Float(), nullable=False),
        sa.Column('feedback_message', sa.String(length=255), nullable=True),
        sa.Column('failure_reasons', sa.JSON(), nullable=True),
    )
    op.create_index(op.f('ix_session_reps_id'), 'session_reps', ['id'], unique=False)
    op.create_index(op.f('ix_session_reps_session_id'), 'session_reps', ['session_id'], unique=False)

    # 3. session_evidence_events
    op.create_table(
        'session_evidence_events',
        sa.Column('id', sa.String(length=64), primary_key=True, nullable=False),
        sa.Column('session_id', sa.String(length=64), sa.ForeignKey('sessions.id', ondelete='CASCADE'), nullable=False),
        sa.Column('event_type', sa.String(length=64), nullable=False),
        sa.Column('start_time_ms', sa.BigInteger(), nullable=False),
        sa.Column('end_time_ms', sa.BigInteger(), nullable=False),
        sa.Column('duration_ms', sa.BigInteger(), nullable=False),
        sa.Column('frame_count', sa.Integer(), nullable=False),
        sa.Column('details', sa.String(length=255), nullable=True),
    )
    op.create_index(op.f('ix_session_evidence_events_id'), 'session_evidence_events', ['id'], unique=False)
    op.create_index(op.f('ix_session_evidence_events_session_id'), 'session_evidence_events', ['session_id'], unique=False)

    # 4. exercise_plans
    op.create_table(
        'exercise_plans',
        sa.Column('id', sa.String(length=64), primary_key=True, nullable=False),
        sa.Column('name', sa.String(length=128), nullable=False),
        sa.Column('description', sa.String(length=512), nullable=True),
        sa.Column('is_active', sa.Boolean(), nullable=True),
        sa.Column('frequency_per_week', sa.Integer(), nullable=True),
        sa.Column('created_at', sa.DateTime(), nullable=True),
        sa.Column('updated_at', sa.DateTime(), nullable=True),
    )
    op.create_index(op.f('ix_exercise_plans_id'), 'exercise_plans', ['id'], unique=False)
    op.create_index(op.f('ix_exercise_plans_is_active'), 'exercise_plans', ['is_active'], unique=False)
    op.create_index(op.f('ix_exercise_plans_created_at'), 'exercise_plans', ['created_at'], unique=False)

    # 5. plan_exercises
    op.create_table(
        'plan_exercises',
        sa.Column('id', sa.String(length=64), primary_key=True, nullable=False),
        sa.Column('plan_id', sa.String(length=64), sa.ForeignKey('exercise_plans.id', ondelete='CASCADE'), nullable=False),
        sa.Column('exercise_type', sa.String(length=64), nullable=False),
        sa.Column('target_sets', sa.Integer(), nullable=True),
        sa.Column('target_reps', sa.Integer(), nullable=True),
        sa.Column('order_index', sa.Integer(), nullable=True),
    )
    op.create_index(op.f('ix_plan_exercises_id'), 'plan_exercises', ['id'], unique=False)
    op.create_index(op.f('ix_plan_exercises_plan_id'), 'plan_exercises', ['plan_id'], unique=False)

    # 6. session_feedback
    op.create_table(
        'session_feedback',
        sa.Column('id', sa.String(length=64), primary_key=True, nullable=False),
        sa.Column('session_id', sa.String(length=64), sa.ForeignKey('sessions.id', ondelete='CASCADE'), nullable=False),
        sa.Column('author', sa.String(length=128), nullable=True),
        sa.Column('message', sa.String(length=1024), nullable=False),
        sa.Column('created_at', sa.DateTime(), nullable=True),
    )
    op.create_index(op.f('ix_session_feedback_id'), 'session_feedback', ['id'], unique=False)
    op.create_index(op.f('ix_session_feedback_session_id'), 'session_feedback', ['session_id'], unique=False)
    op.create_index(op.f('ix_session_feedback_created_at'), 'session_feedback', ['created_at'], unique=False)


def downgrade() -> None:
    op.drop_table('session_feedback')
    op.drop_table('plan_exercises')
    op.drop_table('exercise_plans')
    op.drop_table('session_evidence_events')
    op.drop_table('session_reps')
    op.drop_table('sessions')

