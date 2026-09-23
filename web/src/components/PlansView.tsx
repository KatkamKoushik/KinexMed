import React, { useState, useEffect } from 'react';
import type { Plan, PlanExercise } from '../types/session';
import { fetchPlans, createPlan, updatePlan, deletePlan } from '../api/client';
import { Plus, Check, Trash2, Calendar, Target, Activity, AlertCircle } from 'lucide-react';

const AVAILABLE_EXERCISES = [
  { id: 'sit_to_stand', label: 'Sit-to-Stand' },
  { id: 'squat', label: 'Bilateral Squat' },
  { id: 'forward_lunge', label: 'Forward Lunge' },
  { id: 'reverse_lunge', label: 'Reverse Lunge' },
  { id: 'calf_raise', label: 'Calf Raise' },
  { id: 'knee_extension', label: 'Seated Knee Extension' },
  { id: 'hip_abduction', label: 'Standing Hip Abduction' },
  { id: 'hip_extension', label: 'Standing Hip Extension' },
  { id: 'shoulder_flexion', label: 'Shoulder Flexion' },
  { id: 'shoulder_abduction', label: 'Shoulder Abduction' },
  { id: 'elbow_flexion', label: 'Elbow Flexion (Bicep Curl)' },
  { id: 'elbow_extension', label: 'Elbow Extension (Tricep)' },
  { id: 'marching_in_place', label: 'Marching in Place' },
  { id: 'heel_toe_raise', label: 'Heel-to-Toe Rocking' },
  { id: 'single_leg_balance', label: 'Supported Single-Leg Balance' }
];

export const PlansView: React.FC = () => {
  const [plans, setPlans] = useState<Plan[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [showCreateModal, setShowCreateModal] = useState(false);

  // New plan form state
  const [planName, setPlanName] = useState('');
  const [planDescription, setPlanDescription] = useState('');
  const [frequency, setFrequency] = useState(5);
  const [isActive, setIsActive] = useState(true);
  const [exercises, setExercises] = useState<PlanExercise[]>([
    { exercise_type: 'squat', target_sets: 3, target_reps: 10, order_index: 0 },
    { exercise_type: 'sit_to_stand', target_sets: 3, target_reps: 8, order_index: 1 }
  ]);
  const [isSubmitting, setIsSubmitting] = useState(false);


  const loadPlans = async () => {
    try {
      setLoading(true);
      setError(null);
      const data = await fetchPlans();
      setPlans(data);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to fetch exercise plans';
      setError(msg);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadPlans();
  }, []);

  const handleToggleActive = async (plan: Plan) => {
    try {
      await updatePlan(plan.id, { is_active: !plan.is_active });
      await loadPlans();
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to update plan status';
      alert(msg);
    }
  };

  const handleDeletePlan = async (id: string, name: string) => {
    if (!window.confirm(`Delete rehabilitation plan "${name}"?`)) return;
    try {
      await deletePlan(id);
      await loadPlans();
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to delete plan';
      alert(msg);
    }
  };

  const handleAddExerciseRow = () => {
    setExercises((prev) => [
      ...prev,
      { exercise_type: 'knee_extension', target_sets: 3, target_reps: 10, order_index: prev.length }
    ]);
  };


  const handleRemoveExerciseRow = (index: number) => {
    setExercises((prev) => prev.filter((_, idx) => idx !== index));
  };

  const handleExerciseChange = (index: number, field: keyof PlanExercise, value: string | number) => {
    setExercises((prev) =>
      prev.map((ex, idx) => {
        if (idx === index) {
          return { ...ex, [field]: value };
        }
        return ex;
      })
    );
  };

  const handleCreatePlan = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!planName.trim()) {
      alert('Please enter a plan name');
      return;
    }
    if (exercises.length === 0) {
      alert('Please add at least one exercise to the plan');
      return;
    }

    try {
      setIsSubmitting(true);
      await createPlan({
        name: planName.trim(),
        description: planDescription.trim() || undefined,
        frequency_per_week: frequency,
        is_active: isActive,
        exercises: exercises.map((ex, idx) => ({ ...ex, order_index: idx }))
      });
      setShowCreateModal(false);
      setPlanName('');
      setPlanDescription('');
      setFrequency(5);
      setExercises([
        { exercise_type: 'squat', target_sets: 3, target_reps: 10, order_index: 0 },
        { exercise_type: 'sit_to_stand', target_sets: 3, target_reps: 8, order_index: 1 }
      ]);

      await loadPlans();
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to create plan';
      alert(msg);
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="plans-container">
      <div className="plans-header">
        <div>
          <h2>Prescribed Exercise Plans</h2>
          <p className="plans-subtitle">
            Configure targeted rehabilitation regimens, weekly adherence targets, and prescribed exercise volumes.
          </p>
        </div>
        <button className="btn-primary-action" onClick={() => setShowCreateModal(true)}>
          <Plus size={16} />
          <span>New Prescription Plan</span>
        </button>
      </div>

      {loading && (
        <div className="glass-card" style={{ padding: '3rem', textAlign: 'center', color: '#94A3B8' }}>
          Loading prescribed rehabilitation plans...
        </div>
      )}

      {error && (
        <div className="glass-card" style={{ padding: '2rem', display: 'flex', gap: '1rem', alignItems: 'center', borderColor: '#EF4444' }}>
          <AlertCircle size={24} color="#EF4444" />
          <div>
            <div style={{ fontWeight: 600, color: '#F87171' }}>Unable to load plans</div>
            <div style={{ fontSize: '0.85rem', color: '#94A3B8' }}>{error}</div>
          </div>
        </div>
      )}

      {!loading && !error && plans.length === 0 && (
        <div className="glass-card empty-plans-card">
          <Activity size={48} color="#06B6D4" style={{ margin: '0 auto 1rem', opacity: 0.8 }} />
          <h3>No Prescribed Plans Configured</h3>
          <p>Create a rehabilitation plan with target exercises, sets, reps, and weekly target frequency.</p>
          <button className="btn-primary-action" style={{ marginTop: '1.25rem' }} onClick={() => setShowCreateModal(true)}>
            <Plus size={16} />
            <span>Create First Plan</span>
          </button>
        </div>
      )}

      {!loading && plans.length > 0 && (
        <div className="plans-grid">
          {plans.map((plan) => (
            <div key={plan.id} className={`glass-card plan-card ${plan.is_active ? 'active-plan' : ''}`}>
              <div className="plan-card-header">
                <div className="plan-badge-group">
                  <span className={`plan-status-pill ${plan.is_active ? 'active' : 'inactive'}`}>
                    {plan.is_active ? 'Active Plan' : 'Inactive'}
                  </span>
                  <span className="plan-freq-pill">
                    <Calendar size={13} /> {plan.frequency_per_week} days / week
                  </span>
                </div>
                <div className="plan-actions">
                  <button
                    className="btn-icon-subtle"
                    title={plan.is_active ? 'Deactivate Plan' : 'Set as Active Plan'}
                    onClick={() => handleToggleActive(plan)}
                  >
                    <Check size={16} color={plan.is_active ? '#10B981' : '#64748B'} />
                  </button>
                  <button
                    className="btn-icon-subtle btn-delete"
                    title="Delete Plan"
                    onClick={() => handleDeletePlan(plan.id, plan.name)}
                  >
                    <Trash2 size={16} />
                  </button>
                </div>
              </div>

              <h3 className="plan-title">{plan.name}</h3>
              {plan.description && <p className="plan-description">{plan.description}</p>}

              <div className="plan-exercises-section">
                <div className="plan-exercises-label">Prescribed Routine ({plan.exercises?.length || 0} exercises):</div>
                <div className="plan-exercises-list">
                  {plan.exercises && plan.exercises.length > 0 ? (
                    plan.exercises.map((ex, idx) => (
                      <div key={ex.id || idx} className="plan-exercise-item">
                        <div className="pe-info">
                          <span className="pe-num">{idx + 1}.</span>
                          <span className="pe-name">{ex.exercise_type.replace(/_/g, ' ').toUpperCase()}</span>
                        </div>
                        <div className="pe-targets">
                          <Target size={13} color="#06B6D4" />
                          <span>{ex.target_sets} sets × {ex.target_reps} reps</span>
                        </div>
                      </div>
                    ))
                  ) : (
                    <div style={{ fontSize: '0.8rem', color: '#64748B', fontStyle: 'italic' }}>
                      No exercises specified in this plan.
                    </div>
                  )}
                </div>
              </div>

              <div className="plan-footer">
                <span>Created {new Date(plan.created_at).toLocaleDateString()}</span>
                {plan.is_active && <span className="active-indicator">Target in Patient App</span>}
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Modal for creating a new plan */}
      {showCreateModal && (
        <div className="modal-backdrop">
          <div className="glass-card modal-container">
            <div className="modal-header">
              <h3>Create Rehabilitation Prescription</h3>
              <button className="btn-close" onClick={() => setShowCreateModal(false)}>×</button>
            </div>

            <form onSubmit={handleCreatePlan} className="modal-form">
              <div className="form-group">
                <label>Plan Name</label>
                <input
                  type="text"
                  placeholder="e.g. Post-Op Knee Strengthening Phase 1"
                  value={planName}
                  onChange={(e) => setPlanName(e.target.value)}
                  required
                />
              </div>

              <div className="form-group">
                <label>Description (Optional Clinical Notes)</label>
                <textarea
                  rows={2}
                  placeholder="e.g. Focus on terminal extension and controlled eccentric descent."
                  value={planDescription}
                  onChange={(e) => setPlanDescription(e.target.value)}
                />
              </div>

              <div className="form-row">
                <div className="form-group">
                  <label>Frequency (Days per Week)</label>
                  <input
                    type="number"
                    min={1}
                    max={7}
                    value={frequency}
                    onChange={(e) => setFrequency(parseInt(e.target.value) || 1)}
                  />
                </div>

                <div className="form-group form-checkbox-group">
                  <label>
                    <input
                      type="checkbox"
                      checked={isActive}
                      onChange={(e) => setIsActive(e.target.checked)}
                    />
                    <span>Set as Active Prescription</span>
                  </label>
                </div>
              </div>

              <div className="form-section">
                <div className="form-section-header">
                  <label>Prescribed Exercises</label>
                  <button type="button" className="btn-text-action" onClick={handleAddExerciseRow}>
                    + Add Exercise
                  </button>
                </div>

                <div className="exercises-form-list">
                  {exercises.map((ex, idx) => (
                    <div key={idx} className="exercise-row">
                      <select
                        value={ex.exercise_type}
                        onChange={(e) => handleExerciseChange(idx, 'exercise_type', e.target.value)}
                      >
                        {AVAILABLE_EXERCISES.map((item) => (
                          <option key={item.id} value={item.id}>
                            {item.label}
                          </option>
                        ))}
                      </select>

                      <div className="num-inputs">
                        <label>Sets:</label>
                        <input
                          type="number"
                          min={1}
                          max={10}
                          value={ex.target_sets}
                          onChange={(e) => handleExerciseChange(idx, 'target_sets', parseInt(e.target.value) || 1)}
                        />
                      </div>

                      <div className="num-inputs">
                        <label>Reps:</label>
                        <input
                          type="number"
                          min={1}
                          max={50}
                          value={ex.target_reps}
                          onChange={(e) => handleExerciseChange(idx, 'target_reps', parseInt(e.target.value) || 1)}
                        />
                      </div>

                      {exercises.length > 1 && (
                        <button
                          type="button"
                          className="btn-icon-danger"
                          title="Remove exercise"
                          onClick={() => handleRemoveExerciseRow(idx)}
                        >
                          <Trash2 size={14} />
                        </button>
                      )}
                    </div>
                  ))}
                </div>
              </div>

              <div className="modal-actions">
                <button type="button" className="btn-secondary" onClick={() => setShowCreateModal(false)}>
                  Cancel
                </button>
                <button type="submit" className="btn-primary-action" disabled={isSubmitting}>
                  {isSubmitting ? 'Saving...' : 'Save Prescription Plan'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};
