import joblib
import numpy as np

from scipy.stats import norm
from xgboost import XGBRegressor


class XGBoostModel:

    def __init__(
        self,
        n_estimators=500,
        learning_rate=0.05,
        max_depth=6,
        subsample=0.8,
        colsample_bytree=0.8,
        random_state=42
    ):

        self.model = XGBRegressor(
            n_estimators=n_estimators,
            learning_rate=learning_rate,
            max_depth=max_depth,
            subsample=subsample,
            colsample_bytree=colsample_bytree,
            random_state=random_state,
            objective="reg:squarederror"
        )

        self.residual_std = None

    def train(self, X: np.ndarray, y: np.ndarray):

        self.model.fit(X, y)

        # Fallback only: residuals on the training set are smaller than on
        # unseen data. Call calibrate() with a held-out set after training.
        self.calibrate(X, y)

    def calibrate(self, X: np.ndarray, y: np.ndarray):
        """Sets the spread used by predict_interval() from residuals on X, y."""

        residuals = np.asarray(y) - self.model.predict(X)

        self.residual_std = float(np.std(residuals))

    def predict(self, X: np.ndarray):

        return self.model.predict(X)

    def predict_mean_std(self, X: np.ndarray):

        if self.residual_std is None:
            raise ValueError("Model has no residual_std; train or calibrate it first")

        mean = self.predict(X)

        std = np.full(
            shape=len(mean),
            fill_value=self.residual_std
        )

        return mean, std

    def predict_interval(
        self,
        X: np.ndarray,
        confidence: float = 0.95
    ):

        mean, std = self.predict_mean_std(X)

        alpha = (1 - confidence) / 2

        z_low = norm.ppf(alpha)
        z_high = norm.ppf(1 - alpha)

        lower = mean + z_low * std
        upper = mean + z_high * std

        return lower, upper

    def save(self, path: str):

        payload = {
            "model": self.model,
            "residual_std": self.residual_std
        }

        joblib.dump(payload, path)

    def load(self, path: str):

        payload = joblib.load(path)

        self.model = payload["model"]
        self.residual_std = payload["residual_std"]
