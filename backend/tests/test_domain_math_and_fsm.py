import math
import pytest

# ---------------------------------------------------------
# Python reference implementation mirroring Kotlin Domain Math
# ---------------------------------------------------------

class Landmark:
    def __init__(self, id: int, x: float, y: float, z: float = 0.0, visibility: float = 1.0, presence: float = 1.0):
        self.id = id
        self.x = x
        self.y = y
        self.z = z
        self.visibility = visibility
        self.presence = presence

    def is_confidence_above(self, threshold: float = 0.6) -> bool:
        return self.visibility >= threshold and self.presence >= threshold

    def is_inside_frame(self, margin: float = 0.04) -> bool:
        return margin <= self.x <= (1.0 - margin) and margin <= self.y <= (1.0 - margin)

def calculate_angle_2d(a: Landmark, b: Landmark, c: Landmark) -> float:
    v1x, v1y = a.x - b.x, a.y - b.y
    v2x, v2y = c.x - b.x, c.y - b.y
    dot = v1x * v2x + v1y * v2y
    mag1 = math.sqrt(v1x * v1x + v1y * v1y)
    mag2 = math.sqrt(v2x * v2x + v2y * v2y)
    if mag1 < 1e-6 or mag2 < 1e-6:
        return 180.0
    cos_theta = max(-1.0, min(1.0, dot / (mag1 * mag2)))
    return math.degrees(math.acos(cos_theta))

def evaluate_evidence(landmarks: dict, config_confidence=0.6, margin=0.04, min_span=0.35, max_span=0.96):
    if not landmarks:
        return False, "No person detected. Please step into the camera view."
    
    # Check upper body
    has_upper = any(
        k in landmarks and landmarks[k].is_confidence_above(config_confidence)
        for k in [0, 11, 12] # Nose, L Shoulder, R Shoulder
    )
    if not has_upper:
        return False, "Upper body not clearly visible. Please adjust camera height."

    # Check legs (Left: 23, 25, 27; Right: 24, 26, 28)
    left_ok = all(k in landmarks for k in [23, 25, 27])
    right_ok = all(k in landmarks for k in [24, 26, 28])

    if not left_ok and not right_ok:
        return False, "Insufficient evidence. Please step back so your whole body is visible."

    # Active leg
    active_keys = [23, 25, 27] if left_ok else [24, 26, 28]
    for k in active_keys:
        lm = landmarks[k]
        if not lm.is_confidence_above(config_confidence):
            return False, "Low tracking confidence. Ensure good lighting and unobstructed view."
        if not lm.is_inside_frame(margin):
            return False, "Feet or joints cut off. Move towards center and step back."

    top_y = landmarks.get(0, landmarks.get(11, landmarks.get(12))).y
    bottom_y = max(landmarks[active_keys[1]].y, landmarks[active_keys[2]].y)
    span = abs(bottom_y - top_y)
    if span < min_span:
        return False, "You are too far from the camera. Please move closer."
    if span > max_span:
        return False, "Insufficient evidence. Please step back so your whole body is visible."

    return True, "Good visual evidence."

# ---------------------------------------------------------
# Unit Tests
# ---------------------------------------------------------

def test_geometry_right_angle():
    """Hip at (0, 1), Knee at (0, 0), Ankle at (1, 0) forms exact 90-degree angle."""
    hip = Landmark(23, 0.0, 1.0)
    knee = Landmark(25, 0.0, 0.0)
    ankle = Landmark(27, 1.0, 0.0)
    angle = calculate_angle_2d(hip, knee, ankle)
    assert pytest.approx(angle, 0.01) == 90.0

def test_geometry_straight_leg():
    """Standing erect: Hip at (0.5, 0.4), Knee at (0.5, 0.6), Ankle at (0.5, 0.8) forms 180-degree angle."""
    hip = Landmark(23, 0.5, 0.4)
    knee = Landmark(25, 0.5, 0.6)
    ankle = Landmark(27, 0.5, 0.8)
    angle = calculate_angle_2d(hip, knee, ankle)
    assert pytest.approx(angle, 0.01) == 180.0

def test_geometry_deep_squat():
    """Deep squat: 60-degree flexion."""
    hip = Landmark(23, 0.5, 0.5)
    knee = Landmark(25, 0.7, 0.5)
    ankle = Landmark(27, 0.6, 0.6732)
    angle = calculate_angle_2d(hip, knee, ankle)
    assert pytest.approx(angle, 0.5) == 60.0

def test_evidence_engine_missing_feet():
    """Test when feet are cut off out of frame."""
    landmarks = {
        0: Landmark(0, 0.5, 0.1, visibility=0.9),
        11: Landmark(11, 0.45, 0.25, visibility=0.9),
        23: Landmark(23, 0.48, 0.5, visibility=0.9),
        25: Landmark(25, 0.5, 0.75, visibility=0.9),
        27: Landmark(27, 0.52, 0.98, visibility=0.9), # Cut off at bottom edge
    }
    sufficient, msg = evaluate_evidence(landmarks)
    assert sufficient is False
    assert "cut off" in msg

def test_evidence_engine_insufficient_body_span():
    """Test when person is too far away or span is too small."""
    landmarks = {
        0: Landmark(0, 0.5, 0.4, visibility=0.9),
        11: Landmark(11, 0.45, 0.43, visibility=0.9),
        23: Landmark(23, 0.48, 0.48, visibility=0.9),
        25: Landmark(25, 0.5, 0.52, visibility=0.9),
        27: Landmark(27, 0.52, 0.56, visibility=0.9), # Total span ~0.16 < 0.35
    }
    sufficient, msg = evaluate_evidence(landmarks)
    assert sufficient is False
    assert "too far" in msg

def test_evidence_engine_good_standing():
    """Test valid full-body standing pose."""
    landmarks = {
        0: Landmark(0, 0.5, 0.15, visibility=0.95),
        11: Landmark(11, 0.45, 0.25, visibility=0.95),
        23: Landmark(23, 0.48, 0.50, visibility=0.95),
        25: Landmark(25, 0.50, 0.70, visibility=0.95),
        27: Landmark(27, 0.52, 0.88, visibility=0.95),
    }
    sufficient, msg = evaluate_evidence(landmarks)
    assert sufficient is True
    assert "Good visual evidence" in msg
