#include "ddmc_companion.hpp"
#include <cstdlib>
#include <algorithm>
#include <cmath>

namespace ddmc {

CompanionEngine::CompanionEngine() {
    setCharacter(DOKI_NATSUKI);
}

void CompanionEngine::setCharacter(DokiCharacter character) {
    m_character = character;
    m_particles.clear();
}

const char* CompanionEngine::getCharacterName() const {
    switch (m_character) {
        case DOKI_NATSUKI: return "Natsuki";
        case DOKI_SAYORI: return "Sayori";
        case DOKI_YURI: return "Yuri";
        case DOKI_MONIKA: return "Monika";
        case DOKI_BOCCHI: return "Bocchi";
        default: return "Companion";
    }
}

const char* CompanionEngine::getCharacterColor() const {
    switch (m_character) {
        case DOKI_NATSUKI: return "\033[38;2;244;114;182m"; // Pink
        case DOKI_SAYORI: return "\033[38;2;56;189;248m";  // Sky Cyan / Coral
        case DOKI_YURI: return "\033[38;2;168;85;247m";   // Violet
        case DOKI_MONIKA: return "\033[38;2;34;197;94m";   // Emerald Green
        case DOKI_BOCCHI: return "\033[38;2;251;113;133m"; // Rose
        default: return "\033[37m";
    }
}

void CompanionEngine::setPerfMode(PerfMode mode) {
    m_perfMode = mode;
}

void CompanionEngine::spawnParticle(const std::string& symbol, float x, float y) {
    int maxLimit = (m_perfMode == MODE_POTATO) ? 3 : 8;
    if (static_cast<int>(m_particles.size()) >= maxLimit) {
        m_particles.erase(m_particles.begin());
    }

    CompanionParticle p;
    p.x = x + (static_cast<float>(rand() % 10) - 5.0f);
    p.y = y - 1.0f;
    p.vx = (static_cast<float>(rand() % 10) - 5.0f) * 0.1f;
    p.vy = -0.4f - static_cast<float>(rand() % 5) * 0.1f;
    p.symbol = symbol;
    p.life = 1.0f;
    m_particles.push_back(p);
}

void CompanionEngine::triggerAction(const std::string& action) {
    if (action == "jump") {
        m_state = COMPANION_HAPPY_JUMP;
        m_velY = -4.5f;
        m_stateTimer = 40;
        spawnParticle("♥", m_posX, m_posY);
    } else if (action == "dance") {
        m_state = COMPANION_DANCE;
        m_stateTimer = 60;
        spawnParticle("♫", m_posX, m_posY);
    }
}

void CompanionEngine::update(float dtSec, float bassEnergy, bool isBeat, int stageWidth, int stageHeight) {
    m_animTick++;
    if (m_beatCooldown > 0.0f) m_beatCooldown -= dtSec;

    // React to bass beat drops!
    if (isBeat && m_beatCooldown <= 0.0f && m_posY >= 0.0f) {
        m_state = (rand() % 2 == 0) ? COMPANION_HAPPY_JUMP : COMPANION_DANCE;
        m_velY = -3.2f - bassEnergy * 2.0f;
        m_beatCooldown = 0.35f;
        m_stateTimer = 35;
        spawnParticle((rand() % 2 == 0) ? "♪" : "♫", m_posX, m_posY);
    }

    // State machine transitions
    m_stateTimer--;
    if (m_stateTimer <= 0 && m_posY >= 0.0f) {
        int r = rand() % 100;
        if (r < 40) {
            m_state = COMPANION_STAND;
            m_velX = 0.0f;
            m_stateTimer = 40 + rand() % 40;
        } else if (r < 75) {
            m_state = COMPANION_WALK;
            m_facingRight = (rand() % 2 == 0);
            m_velX = m_facingRight ? 1.0f : -1.0f;
            m_stateTimer = 50 + rand() % 50;
        } else if (r < 90) {
            m_state = COMPANION_SIT;
            m_velX = 0.0f;
            m_stateTimer = 60 + rand() % 60;
        } else {
            m_state = COMPANION_DANCE;
            m_velX = 0.0f;
            m_stateTimer = 45;
        }
    }

    // Physics integration
    m_posX += m_velX * dtSec * 12.0f;
    m_posY += m_velY * dtSec * 12.0f;

    // Gravity
    if (m_posY < 0.0f) {
        m_velY += 9.8f * dtSec * 4.0f;
    } else {
        m_posY = 0.0f;
        m_velY = 0.0f;
    }

    // Boundaries
    float minX = 2.0f;
    float maxX = static_cast<float>(std::max(10, stageWidth - 14));
    if (m_posX < minX) {
        m_posX = minX;
        m_facingRight = true;
        m_velX = -m_velX;
    } else if (m_posX > maxX) {
        m_posX = maxX;
        m_facingRight = false;
        m_velX = -m_velX;
    }

    // Update particles
    for (auto it = m_particles.begin(); it != m_particles.end();) {
        it->x += it->vx;
        it->y += it->vy;
        it->life -= dtSec * 1.8f;
        if (it->life <= 0.0f) {
            it = m_particles.erase(it);
        } else {
            ++it;
        }
    }
}

std::vector<std::string> CompanionEngine::getRenderLines() const {
    std::vector<std::string> lines;
    bool blink = (m_animTick % 60 < 4);
    bool step = (m_animTick / 8) % 2 == 0;

    switch (m_character) {
        case DOKI_NATSUKI:
            if (m_state == COMPANION_HAPPY_JUMP) {
                lines.push_back("  \\(^o^)/  *bakka!*");
                lines.push_back("   ( >.< )         ");
                lines.push_back("    /   \\  ~♥      ");
            } else if (m_state == COMPANION_DANCE) {
                lines.push_back("   \\( >w<)/ ♫      ");
                lines.push_back("    (  \"  )         ");
                lines.push_back(step ? "    /  |            " : "    |  \\            ");
            } else if (m_state == COMPANION_SIT) {
                lines.push_back("    ( -.- )  *munch*");
                lines.push_back("    (\"\"\"\"\") [cupcake]");
                lines.push_back("     c c            ");
            } else {
                lines.push_back(blink ? "    ( -.- )         " : "    ( >.< )         ");
                lines.push_back("   /(  \"  )\\        ");
                lines.push_back(step ? "    /   |           " : "    |   \\           ");
            }
            break;

        case DOKI_SAYORI:
            if (m_state == COMPANION_HAPPY_JUMP) {
                lines.push_back("  \\(^▽^)/  *ehehe!*");
                lines.push_back("   ( ^.^ )         ");
                lines.push_back("    /   \\   ♪      ");
            } else if (m_state == COMPANION_DANCE) {
                lines.push_back("   ~(^o^)~  ♫      ");
                lines.push_back("    (  v  )         ");
                lines.push_back(step ? "    /  |            " : "    |  \\            ");
            } else {
                lines.push_back(blink ? "    ( -.- )         " : "    ( ^.^ )         ");
                lines.push_back("   /(  v  )\\  *sun* ");
                lines.push_back(step ? "    /   |           " : "    |   \\           ");
            }
            break;

        case DOKI_YURI:
            if (m_state == COMPANION_SIT) {
                lines.push_back("    ( u.u )  *tea*  ");
                lines.push_back("    (|---|) [book]  ");
                lines.push_back("     c c            ");
            } else {
                lines.push_back(blink ? "    ( -.- )         " : "    ( u.u )         ");
                lines.push_back("   /( | | )\\        ");
                lines.push_back(step ? "    /   |           " : "    |   \\           ");
            }
            break;

        case DOKI_MONIKA:
            if (m_state == COMPANION_HAPPY_JUMP || m_state == COMPANION_DANCE) {
                lines.push_back("   ( *.* )/ *ok,everyone!*");
                lines.push_back("   /( | | )         ");
                lines.push_back(step ? "    /   |   ♫       " : "    |   \\   ♪       ");
            } else {
                lines.push_back(blink ? "    ( -.- )         " : "    ( ^_~ )         ");
                lines.push_back("   /( | | )\\        ");
                lines.push_back(step ? "    /   |           " : "    |   \\           ");
            }
            break;

        case DOKI_BOCCHI:
            if (m_state == COMPANION_HAPPY_JUMP) {
                lines.push_back("   ( >o< )! *GYAAAH*");
                lines.push_back("  =[guitar]=        ");
                lines.push_back("    /   \\           ");
            } else if (m_state == COMPANION_SIT) {
                lines.push_back("   ( ;_; )  *box*   ");
                lines.push_back("   [_____]          ");
                lines.push_back("     c c            ");
            } else {
                lines.push_back(blink ? "    ( -_- )         " : "    ( o_o )         ");
                lines.push_back("  =[guitar]=        ");
                lines.push_back(step ? "    /   |           " : "    |   \\           ");
            }
            break;
    }

    return lines;
}

} // namespace ddmc
